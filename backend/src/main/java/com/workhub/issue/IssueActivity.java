package com.workhub.issue;

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

/** One entry of an issue's append-only history. */
@Entity
@Table(name = "issue_activity")
public class IssueActivity {

	public enum Kind { CREATED, CHANGED }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "issue_id", nullable = false, updatable = false)
	private UUID issueId;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private Kind kind;

	@Column(length = 32, updatable = false)
	private String field;

	@Column(name = "old_value", length = 500, updatable = false)
	private String oldValue;

	@Column(name = "new_value", length = 500, updatable = false)
	private String newValue;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IssueActivity() {
	}

	IssueActivity(UUID issueId, UUID actorId, Kind kind, String field, String oldValue, String newValue) {
		this.issueId = issueId;
		this.actorId = actorId;
		this.kind = kind;
		this.field = field;
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorId() {
		return actorId;
	}

	public Kind getKind() {
		return kind;
	}

	public String getField() {
		return field;
	}

	public String getOldValue() {
		return oldValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
