package com.workhub.document;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.common.PageResponse;
import com.workhub.document.DocumentLoader.Loaded;
import com.workhub.document.DocumentResponses.DocumentDetail;
import com.workhub.document.DocumentResponses.DocumentSummary;
import com.workhub.document.DocumentResponses.VersionDetail;
import com.workhub.document.DocumentResponses.VersionSummary;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.meeting.MeetingRepository;
import com.workhub.sprint.SprintRepository;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project documents with immutable version history. Anyone who can see the project can read; EDIT_DOCUMENTS (every
 * role except stakeholders) creates, edits, archives and links; deleting also needs to be the author or a project
 * manager. All writes need a non-archived project. Saves carry the version the editor started from, so a stale
 * save is refused (409 DOCUMENT_CONFLICT) instead of silently overwriting someone else's text.
 */
@Service
@Transactional
public class DocumentService {

	private final AccessService access;

	private final DocumentLoader loader;

	private final DocumentRepository documents;

	private final DocumentFolderRepository folders;

	private final DocumentVersionRepository versions;

	private final DocumentLinkRepository links;

	private final DocumentMapper mapper;

	private final IssueRepository issues;

	private final SprintRepository sprints;

	private final MeetingRepository meetings;

	private final CollabService collab;

	private final Clock clock;

	DocumentService(AccessService access, DocumentLoader loader, DocumentRepository documents,
			DocumentFolderRepository folders, DocumentVersionRepository versions, DocumentLinkRepository links,
			DocumentMapper mapper, IssueRepository issues, SprintRepository sprints, MeetingRepository meetings,
			CollabService collab, Clock clock) {
		this.collab = collab;
		this.access = access;
		this.loader = loader;
		this.documents = documents;
		this.folders = folders;
		this.versions = versions;
		this.links = links;
		this.mapper = mapper;
		this.issues = issues;
		this.sprints = sprints;
		this.meetings = meetings;
		this.clock = clock;
	}

	// ---- list / read ----

	@Transactional(readOnly = true)
	public PageResponse<DocumentSummary> list(UUID userId, UUID projectId, DocumentFilter filter, int page, int size) {
		access.project(userId, projectId);
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100),
				Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
		Page<Document> result = documents.findAll(filter.toSpecification(projectId), pageable);
		return PageResponse.of(result, mapper.summaries(result.getContent()));
	}

	@Transactional(readOnly = true)
	public DocumentDetail get(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		return mapper.detail(loaded.document(), loaded.access());
	}

	// ---- create / update ----

	public DocumentDetail create(UUID userId, UUID projectId, DocumentRequests.Create request) {
		ProjectAccess caller = access.project(userId, projectId)
			.require(ProjectPermission.EDIT_DOCUMENTS)
			.requireActive();
		UUID folderId = checkFolder(projectId, request.folderId());
		String content = (request.content() == null) ? "" : request.content();
		Document document = documents.saveAndFlush(
				new Document(projectId, folderId, request.title().strip(), request.type(), content, userId));
		versions.save(new DocumentVersion(document, null, userId));
		return mapper.detail(document, caller);
	}

	public DocumentDetail update(UUID userId, UUID documentId, DocumentRequests.Update request) {
		Loaded loaded = writable(userId, documentId);
		Document document = loaded.document();
		if (request.baseVersion() != document.getVersionNumber()) {
			throw conflict();
		}
		String title = request.title().strip();
		boolean textChanged = !title.equals(document.getTitle()) || !request.content().equals(document.getContent());
		document.updateMetadata(request.type(), checkFolder(document.getProjectId(), request.folderId()), userId);
		if (textChanged) {
			document.revise(title, request.content(), userId);
		}
		flush(document);
		if (textChanged) {
			versions.save(new DocumentVersion(document, trimNote(request.note()), userId));
			// Anyone editing live now holds the old text: they must reload.
			collab.reset(documentId);
		}
		return mapper.detail(document, loaded.access());
	}

	public DocumentDetail setArchived(UUID userId, UUID documentId, boolean archived) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		loaded.document().setStatus(archived ? DocumentStatus.ARCHIVED : DocumentStatus.ACTIVE, userId);
		flush(loaded.document());
		return mapper.detail(loaded.document(), loaded.access());
	}

	/** Soft delete: the row stays (with its versions) but disappears from every query. */
	public void delete(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		ProjectAccess caller = loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		if (!loaded.document().getCreatedBy().equals(userId)) {
			caller.require(ProjectPermission.UPDATE_PROJECT);
		}
		loaded.document().markDeleted(clock.instant());
	}

	// ---- versions ----

	@Transactional(readOnly = true)
	public List<VersionSummary> versions(UUID userId, UUID documentId) {
		loader.load(userId, documentId);
		List<DocumentVersion> rows = versions.findAllByDocumentIdOrderByVersionNumberDesc(documentId);
		var people = mapper.userRefs(rows.stream().map(DocumentVersion::getCreatedBy).toList());
		return rows.stream()
			.map((v) -> new VersionSummary(v.getVersionNumber(), v.getTitle(), v.getNote(), people.get(v.getCreatedBy()),
					v.getCreatedAt(), v.getContent().length()))
			.toList();
	}

	@Transactional(readOnly = true)
	public VersionDetail version(UUID userId, UUID documentId, int number) {
		loader.load(userId, documentId);
		DocumentVersion v = findVersion(documentId, number);
		UserRef author = mapper.userRefs(List.of(v.getCreatedBy())).get(v.getCreatedBy());
		return new VersionDetail(v.getVersionNumber(), v.getTitle(), v.getContent(), v.getNote(), author,
				v.getCreatedAt());
	}

	/** Makes an old version current again by adding a new version with its text; history is never rewritten. */
	public DocumentDetail restore(UUID userId, UUID documentId, int number) {
		Loaded loaded = writable(userId, documentId);
		DocumentVersion old = findVersion(documentId, number);
		Document document = loaded.document();
		document.revise(old.getTitle(), old.getContent(), userId);
		flush(document);
		versions.save(new DocumentVersion(document, "Khôi phục từ phiên bản " + number, userId));
		collab.reset(documentId);
		return mapper.detail(document, loaded.access());
	}

	// ---- links ----

	public DocumentDetail addLink(UUID userId, UUID documentId, DocumentRequests.AddLink request) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		UUID projectId = loaded.document().getProjectId();
		boolean found = switch (request.targetType()) {
			case ISSUE -> issues.findById(request.targetId()).filter((i) -> i.getProjectId().equals(projectId)).isPresent();
			case SPRINT -> sprints.findById(request.targetId()).filter((s) -> s.getProjectId().equals(projectId)).isPresent();
			case MEETING -> meetings.findById(request.targetId()).filter((m) -> m.getProjectId().equals(projectId)).isPresent();
		};
		if (!found) {
			throw ApiException.notFound("LINK_TARGET_NOT_FOUND", "Target not found in this project");
		}
		if (!links.existsByDocumentIdAndTargetTypeAndTargetId(documentId, request.targetType(), request.targetId())) {
			links.save(new DocumentLink(documentId, request.targetType(), request.targetId(), userId));
		}
		return mapper.detail(loaded.document(), loaded.access());
	}

	public DocumentDetail removeLink(UUID userId, UUID documentId, LinkTargetType type, UUID targetId) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		links.deleteByDocumentIdAndTargetTypeAndTargetId(documentId, type, targetId);
		return mapper.detail(loaded.document(), loaded.access());
	}

	// ---- helpers ----

	/** The document for a change to its text: editable permission, active project and not archived. */
	private Loaded writable(UUID userId, UUID documentId) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		if (loaded.document().getStatus() == DocumentStatus.ARCHIVED) {
			throw ApiException.conflict("DOCUMENT_ARCHIVED", "Document is archived");
		}
		return loaded;
	}

	private UUID checkFolder(UUID projectId, UUID folderId) {
		if (folderId == null) {
			return null;
		}
		return folders.findById(folderId)
			.filter((f) -> f.getProjectId().equals(projectId))
			.orElseThrow(DocumentLoader::folderNotFound)
			.getId();
	}

	private DocumentVersion findVersion(UUID documentId, int number) {
		return versions.findByDocumentIdAndVersionNumber(documentId, number)
			.orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "Version not found"));
	}

	private void flush(Document document) {
		try {
			documents.saveAndFlush(document);
		}
		catch (OptimisticLockingFailureException ex) {
			throw conflict();
		}
	}

	private static ApiException conflict() {
		return ApiException.conflict("DOCUMENT_CONFLICT", "The document was changed by someone else");
	}

	private static String trimNote(String note) {
		return (note == null || note.isBlank()) ? null : note.strip();
	}

}
