package com.workhub.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueResponses.UserRef;

public final class DocumentResponses {

	private DocumentResponses() {
	}

	public record FolderResponse(UUID id, UUID parentId, String name, long documentCount) {
	}

	public record DocumentSummary(UUID id, String title, DocumentType type, UUID folderId, DocumentStatus status,
			int version, String excerpt, UserRef updatedBy, Instant updatedAt, int attachmentCount, boolean managed) {
	}

	public record AttachmentResponse(UUID id, String fileName, String contentType, long sizeBytes, UserRef uploadedBy,
			Instant createdAt) {
	}

	/** {@code label} is "KEY-12 Title" for an issue or the sprint's name; {@code detail} its current status. */
	public record LinkResponse(LinkTargetType targetType, UUID targetId, String label, String detail) {
	}

	public record DocumentDetail(UUID id, UUID projectId, String title, DocumentType type, UUID folderId,
			DocumentStatus status, int version, String content, UserRef createdBy, UserRef updatedBy,
			Instant createdAt, Instant updatedAt, List<AttachmentResponse> attachments, List<LinkResponse> links,
			boolean canEdit, boolean canDelete, String externalRef, boolean managed) {
	}

	public record VersionSummary(int version, String title, String note, UserRef author, Instant createdAt,
			int length) {
	}

	public record VersionDetail(int version, String title, String content, String note, UserRef author,
			Instant createdAt) {
	}

}
