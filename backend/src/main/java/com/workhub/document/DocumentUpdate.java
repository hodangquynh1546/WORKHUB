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

/** One binary Yjs update, in the order the server accepted it ({@code seq} is global and strictly increasing). */
@Entity
@Table(name = "document_updates")
public class DocumentUpdate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long seq;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(nullable = false, updatable = false)
	private int epoch;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false, updatable = false)
	private byte[] data;

	@Column(name = "author_id", updatable = false)
	private UUID authorId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentUpdate() {
	}

	DocumentUpdate(UUID documentId, int epoch, byte[] data, UUID authorId) {
		this.documentId = documentId;
		this.epoch = epoch;
		this.data = data;
		this.authorId = authorId;
	}

	public Long getSeq() {
		return seq;
	}

	public byte[] getData() {
		return data;
	}

}
