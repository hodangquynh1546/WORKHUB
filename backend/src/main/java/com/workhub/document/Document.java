package com.workhub.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A project document. {@code content} is the current text; every change to title or content also adds an immutable
 * {@link DocumentVersion}. Soft-deleted rows are invisible to every query.
 */
@Entity
@Table(name = "documents")
@SQLRestriction("deleted_at is null")
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "folder_id")
	private UUID folderId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "doc_type", nullable = false, length = 40)
	private DocumentType type;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private DocumentStatus status = DocumentStatus.ACTIVE;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Version
	@Column(name = "lock_version", nullable = false)
	private long lockVersion;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Document() {
	}

	Document(UUID projectId, UUID folderId, String title, DocumentType type, String content, UUID author) {
		this.projectId = projectId;
		this.folderId = folderId;
		this.title = title;
		this.type = type;
		this.content = content;
		this.createdBy = author;
		this.updatedBy = author;
		this.versionNumber = 1;
	}

	/** Changes title and content, moving to the next version number; the caller stores the matching version row. */
	void revise(String title, String content, UUID author) {
		this.title = title;
		this.content = content;
		this.updatedBy = author;
		this.versionNumber++;
	}

	void updateMetadata(DocumentType type, UUID folderId, UUID author) {
		this.type = type;
		this.folderId = folderId;
		this.updatedBy = author;
	}

	void setStatus(DocumentStatus status, UUID author) {
		this.status = status;
		this.updatedBy = author;
	}

	void markDeleted(Instant now) {
		this.deletedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getFolderId() {
		return folderId;
	}

	public String getTitle() {
		return title;
	}

	public DocumentType getType() {
		return type;
	}

	public String getContent() {
		return content;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
