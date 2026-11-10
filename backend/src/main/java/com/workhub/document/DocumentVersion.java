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

/** One immutable snapshot of a document's title and full text. Rows are only ever inserted. */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Column(nullable = false, updatable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(nullable = false, updatable = false, columnDefinition = "TEXT")
	private String content;

	@Column(updatable = false, length = 200)
	private String note;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentVersion() {
	}

	DocumentVersion(Document document, String note, UUID author) {
		this.documentId = document.getId();
		this.versionNumber = document.getVersionNumber();
		this.title = document.getTitle();
		this.content = document.getContent();
		this.note = note;
		this.createdBy = author;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getNote() {
		return note;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
