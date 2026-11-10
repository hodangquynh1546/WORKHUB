package com.workhub.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.document.DocumentLoader.Loaded;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server side of collaborative editing. It stores and relays the binary Yjs data without interpreting it: an
 * append-only log of updates and, now and then, a full-state snapshot uploaded by a client together with the plain
 * text it represents. A snapshot is only accepted if it covers at least as many updates as the current one, so a
 * client that is behind cannot move the stored text backwards; updates the snapshot contains are then dropped.
 *
 * <p>Plain text ({@code documents.content}) is a working copy refreshed by snapshots; a new version is added when
 * someone asks for one, or when the text changed and the last version is older than {@value #VERSION_EVERY_MINUTES}
 * minutes. Replacing the text from outside (restoring a version, a plain save) bumps the epoch, which tells every
 * open editor to reload.
 */
@Service
@Transactional
public class CollabService {

	static final int MAX_UPDATE_BYTES = 1024 * 1024;

	static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;

	static final int VERSION_EVERY_MINUTES = 10;

	/** What a client needs to build the document: the snapshot (if any) and the updates after it, in order. */
	public record State(int epoch, String snapshot, long snapshotSeq, List<UpdateRow> updates, long lastSeq, boolean empty,
			boolean canEdit, String content) {
	}

	public record UpdateRow(long seq, String update) {
	}

	/** {@code seq} is the last update the uploading client had applied; {@code text} what its document reads. */
	public record Snapshot(int epoch, long seq, String state, String text, boolean createVersion, String note) {
	}

	public record SnapshotResult(boolean applied, int version) {
	}

	/** Result of joining a room: what the caller may do and whether it must seed the CRDT from the stored text. */
	public record Join(int epoch, boolean canEdit, boolean needsInit) {
	}

	/** Raised after commit so open editors reload (the sessions component listens). */
	public record ResetEvent(UUID documentId, int epoch) {
	}

	private final DocumentLoader loader;

	private final DocumentRepository documents;

	private final DocumentVersionRepository versions;

	private final DocumentCollabRepository collabs;

	private final DocumentUpdateRepository updates;

	private final DocumentFolderRepository folders;

	private final DocumentMapper mapper;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	CollabService(DocumentLoader loader, DocumentRepository documents, DocumentVersionRepository versions, DocumentCollabRepository collabs,
			DocumentUpdateRepository updates, DocumentFolderRepository folders, DocumentMapper mapper,
			ApplicationEventPublisher events, Clock clock) {
		this.folders = folders;
		this.mapper = mapper;
		this.loader = loader;
		this.documents = documents;
		this.versions = versions;
		this.collabs = collabs;
		this.updates = updates;
		this.events = events;
		this.clock = clock;
	}

	// ---- reading ----

	public State state(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		DocumentCollab collab = ensure(documentId, true);
		List<DocumentUpdate> rows = updates.findAllByDocumentIdAndEpochAndSeqGreaterThanOrderBySeqAsc(documentId, collab.getEpoch(), collab.getSnapshotSeq());
		long lastSeq = rows.isEmpty() ? collab.getSnapshotSeq() : rows.get(rows.size() - 1).getSeq();
		return new State(collab.getEpoch(), (collab.getSnapshot() == null) ? null : Base64.getEncoder().encodeToString(collab.getSnapshot()),
				collab.getSnapshotSeq(),
				rows.stream().map((u) -> new UpdateRow(u.getSeq(), Base64.getEncoder().encodeToString(u.getData()))).toList(), lastSeq,
				collab.getSnapshot() == null && rows.isEmpty(), canEdit(loaded), loaded.document().getContent());
	}

	/** Joining a room: needs to see the document; says whether the caller can edit and must seed an empty CRDT. */
	public Join join(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		DocumentCollab collab = ensure(documentId, true);
		boolean empty = collab.getSnapshot() == null && !updates.existsByDocumentIdAndEpoch(documentId, collab.getEpoch());
		boolean edit = canEdit(loaded);
		return new Join(collab.getEpoch(), edit, edit && empty && !loaded.document().getContent().isEmpty());
	}

	// ---- writing ----

	/** Stores one update from an editor and returns its sequence number. */
	public long append(UUID userId, UUID documentId, int epoch, byte[] data) {
		Loaded loaded = writable(userId, documentId);
		if (data.length == 0 || data.length > MAX_UPDATE_BYTES) {
			throw ApiException.badRequest("UPDATE_TOO_LARGE", "Update must be between 1 byte and 1 MB");
		}
		DocumentCollab collab = ensure(documentId, false);
		if (collab.getEpoch() != epoch) {
			throw ApiException.conflict("STALE_EPOCH", "The document was replaced; reload it");
		}
		return updates.saveAndFlush(new DocumentUpdate(loaded.document().getId(), epoch, data, userId)).getSeq();
	}

	public SnapshotResult snapshot(UUID userId, UUID documentId, Snapshot request) {
		Loaded loaded = writable(userId, documentId);
		if (request.text() == null || request.text().length() > DocumentRequests.MAX_CONTENT) {
			throw ApiException.badRequest("CONTENT_TOO_LARGE", "Document text is limited to " + DocumentRequests.MAX_CONTENT + " characters");
		}
		byte[] state = decode(request.state());
		if (state.length == 0 || state.length > MAX_SNAPSHOT_BYTES) {
			throw ApiException.badRequest("SNAPSHOT_TOO_LARGE", "Snapshot must be between 1 byte and 8 MB");
		}
		ensure(documentId, false);
		DocumentCollab collab = collabs.findForWrite(documentId).orElseThrow();
		if (collab.getEpoch() != request.epoch()) {
			throw ApiException.conflict("STALE_EPOCH", "The document was replaced; reload it");
		}
		Document document = loaded.document();
		Instant now = clock.instant();
		if (request.seq() < collab.getSnapshotSeq()) {
			// A client that is behind the stored snapshot must not move the text backwards.
			return new SnapshotResult(false, document.getVersionNumber());
		}
		collab.compactTo(state, request.seq(), now);
		updates.deleteUpTo(documentId, request.seq());

		DocumentVersion latest = versions.findFirstByDocumentIdOrderByVersionNumberDesc(documentId).orElse(null);
		boolean textChanged = latest == null || !request.text().equals(latest.getContent());
		boolean due = request.createVersion() || (latest != null && Duration.between(latest.getCreatedAt(), now).toMinutes() >= VERSION_EVERY_MINUTES);
		try {
			if (textChanged && due) {
				document.revise(document.getTitle(), request.text(), userId);
				documents.saveAndFlush(document);
				versions.save(new DocumentVersion(document, trim(request.note()), userId));
			}
			else if (!request.text().equals(document.getContent())) {
				document.setWorkingCopy(request.text(), userId);
				documents.saveAndFlush(document);
			}
		}
		catch (OptimisticLockingFailureException ex) {
			throw ApiException.conflict("CONFLICT", "Another save happened at the same time; try again");
		}
		return new SnapshotResult(true, document.getVersionNumber());
	}

	/** Title, type and folder; the text goes through the editor. */
	public DocumentResponses.DocumentDetail updateMeta(UUID userId, UUID documentId, String title, DocumentType type, UUID folderId) {
		Loaded loaded = writable(userId, documentId);
		Document document = loaded.document();
		if (folderId != null && folders.findById(folderId).filter((f) -> f.getProjectId().equals(document.getProjectId())).isEmpty()) {
			throw DocumentLoader.folderNotFound();
		}
		document.rename(title.strip(), userId);
		document.updateMetadata(type, folderId, userId);
		documents.saveAndFlush(document);
		return mapper.detail(document, loaded.access());
	}

	/** Called when the text is replaced from outside the editor; open editors are told to reload. */
	public void reset(UUID documentId) {
		DocumentCollab collab = collabs.findForWrite(documentId).orElse(null);
		if (collab == null) {
			return;
		}
		collab.reset(clock.instant());
		updates.deleteAll(documentId);
		events.publishEvent(new ResetEvent(documentId, collab.getEpoch()));
	}

	// ---- helpers ----

	private DocumentCollab ensure(UUID documentId, boolean shared) {
		return (shared ? collabs.findForRead(documentId) : collabs.findById(documentId))
			.orElseGet(() -> collabs.saveAndFlush(new DocumentCollab(documentId, clock.instant())));
	}

	private Loaded writable(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		if (loaded.document().getStatus() == DocumentStatus.ARCHIVED) {
			throw ApiException.conflict("DOCUMENT_ARCHIVED", "Document is archived");
		}
		return loaded;
	}

	private static boolean canEdit(Loaded loaded) {
		return loaded.access().permissions().contains(ProjectPermission.EDIT_DOCUMENTS) && !loaded.access().project().isArchived()
				&& loaded.document().getStatus() == DocumentStatus.ACTIVE;
	}

	private static byte[] decode(String base64) {
		try {
			return Base64.getDecoder().decode(base64 == null ? "" : base64);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("INVALID_DATA", "Not valid base64");
		}
	}

	private static String trim(String note) {
		return (note == null || note.isBlank()) ? null : note.strip();
	}

}
