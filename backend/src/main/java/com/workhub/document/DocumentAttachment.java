package com.workhub.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A file attached to a document, stored in the database. Lists use {@link AttachmentRow} so bytes are not loaded. */
@Entity
@Table(name = "document_attachments")
public class DocumentAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false)
	private byte[] data;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentAttachment() {
	}

	DocumentAttachment(UUID documentId, String fileName, String contentType, byte[] data, UUID uploadedBy) {
		this.documentId = documentId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = data.length;
		this.data = data;
		this.uploadedBy = uploadedBy;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public byte[] getData() {
		return data;
	}

	/** List projection without the file bytes. */
	public record AttachmentRow(UUID id, String fileName, String contentType, long sizeBytes, UUID uploadedBy,
			Instant createdAt) {
	}

}
