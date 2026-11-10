package com.workhub.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Collaborative-editing state of one document: the current epoch and the latest compacted snapshot. */
@Entity
@Table(name = "document_collab")
public class DocumentCollab {

	@Id
	@Column(name = "document_id")
	private UUID documentId;

	@Column(nullable = false)
	private int epoch;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column
	private byte[] snapshot;

	@Column(name = "snapshot_seq", nullable = false)
	private long snapshotSeq;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DocumentCollab() {
	}

	DocumentCollab(UUID documentId, Instant now) {
		this.documentId = documentId;
		this.epoch = 1;
		this.snapshotSeq = 0;
		this.updatedAt = now;
	}

	void compactTo(byte[] snapshot, long seq, Instant now) {
		this.snapshot = snapshot;
		this.snapshotSeq = seq;
		this.updatedAt = now;
	}

	/** Forgets all CRDT state: the text now comes from the document row again. */
	void reset(Instant now) {
		this.epoch++;
		this.snapshot = null;
		this.snapshotSeq = 0;
		this.updatedAt = now;
	}

	public int getEpoch() {
		return epoch;
	}

	public byte[] getSnapshot() {
		return snapshot;
	}

	public long getSnapshotSeq() {
		return snapshotSeq;
	}

}
