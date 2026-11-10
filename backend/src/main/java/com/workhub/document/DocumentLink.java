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

import org.hibernate.annotations.CreationTimestamp;

/** A document attached to an issue or a sprint of the same project. */
@Entity
@Table(name = "document_links")
public class DocumentLink {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 16)
	private LinkTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentLink() {
	}

	DocumentLink(UUID documentId, LinkTargetType targetType, UUID targetId, UUID createdBy) {
		this.documentId = documentId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.createdBy = createdBy;
	}

	public LinkTargetType getTargetType() {
		return targetType;
	}

	public UUID getTargetId() {
		return targetId;
	}

}
