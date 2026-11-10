package com.workhub.document;

import java.util.UUID;

import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.document.DocumentLoader.Loaded;
import com.workhub.document.DocumentResponses.AttachmentResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Files attached to a document (kept in the database; at most 20 per document, 10 MB each). */
@Service
@Transactional
public class DocumentAttachmentService {

	static final int MAX_PER_DOCUMENT = 20;

	static final long MAX_BYTES = 10L * 1024 * 1024;

	private final DocumentLoader loader;

	private final DocumentAttachmentRepository attachments;

	private final DocumentMapper mapper;

	DocumentAttachmentService(DocumentLoader loader, DocumentAttachmentRepository attachments, DocumentMapper mapper) {
		this.loader = loader;
		this.attachments = attachments;
		this.mapper = mapper;
	}

	public AttachmentResponse upload(UUID userId, UUID documentId, String originalName, String contentType, byte[] data) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		if (loaded.document().getStatus() == DocumentStatus.ARCHIVED) {
			throw ApiException.conflict("DOCUMENT_ARCHIVED", "Document is archived");
		}
		if (data.length == 0) {
			throw ApiException.badRequest("FILE_EMPTY", "The file is empty");
		}
		if (data.length > MAX_BYTES) {
			throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File too large");
		}
		if (attachments.countByDocumentId(documentId) >= MAX_PER_DOCUMENT) {
			throw ApiException.conflict("TOO_MANY_ATTACHMENTS", "A document can have at most " + MAX_PER_DOCUMENT + " files");
		}
		DocumentAttachment saved = attachments.saveAndFlush(new DocumentAttachment(documentId, safeName(originalName),
				(contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType, data, userId));
		var uploader = mapper.userRefs(java.util.List.of(userId)).get(userId);
		return new AttachmentResponse(saved.getId(), saved.getFileName(), saved.getContentType(), data.length, uploader,
				saved.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public DocumentAttachment download(UUID userId, UUID documentId, UUID attachmentId) {
		loader.load(userId, documentId);
		return find(documentId, attachmentId);
	}

	public void delete(UUID userId, UUID documentId, UUID attachmentId) {
		Loaded loaded = loader.load(userId, documentId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		attachments.delete(find(documentId, attachmentId));
	}

	private DocumentAttachment find(UUID documentId, UUID attachmentId) {
		return attachments.findById(attachmentId)
			.filter((a) -> a.getDocumentId().equals(documentId))
			.orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment not found"));
	}

	/** Keeps only the file's own name: browsers may send a path, and the name ends up in a download header. */
	private static String safeName(String original) {
		String name = (original == null) ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
		if (name.isEmpty()) {
			name = "file";
		}
		return (name.length() > 255) ? name.substring(name.length() - 255) : name;
	}

}
