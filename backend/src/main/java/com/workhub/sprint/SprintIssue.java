package com.workhub.sprint;

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

import com.workhub.issue.IssueStatus;

/** One stretch of an issue's time in a sprint. Open while {@code removedAt} is null. Rows are never deleted. */
@Entity
@Table(name = "sprint_issues")
public class SprintIssue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "sprint_id", nullable = false, updatable = false)
	private UUID sprintId;

	@Column(name = "issue_id", nullable = false, updatable = false)
	private UUID issueId;

	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	@Column(name = "added_by", nullable = false, updatable = false)
	private UUID addedBy;

	@Column(name = "added_points", updatable = false)
	private Integer addedPoints;

	@Column(name = "added_after_start", nullable = false, updatable = false)
	private boolean addedAfterStart;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "removed_by")
	private UUID removedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "removal_reason", length = 16)
	private RemovalReason removalReason;

	@Column(name = "removed_points")
	private Integer removedPoints;

	@Enumerated(EnumType.STRING)
	@Column(name = "final_status", length = 16)
	private IssueStatus finalStatus;

	@Column(name = "final_points")
	private Integer finalPoints;

	protected SprintIssue() {
	}

	SprintIssue(UUID sprintId, UUID issueId, Instant addedAt, UUID addedBy, Integer addedPoints,
			boolean addedAfterStart) {
		this.sprintId = sprintId;
		this.issueId = issueId;
		this.addedAt = addedAt;
		this.addedBy = addedBy;
		this.addedPoints = addedPoints;
		this.addedAfterStart = addedAfterStart;
	}

	void close(Instant now, UUID by, RemovalReason reason, Integer points) {
		this.removedAt = now;
		this.removedBy = by;
		this.removalReason = reason;
		this.removedPoints = points;
	}

	void recordOutcome(IssueStatus status, Integer points) {
		this.finalStatus = status;
		this.finalPoints = points;
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public UUID getIssueId() {
		return issueId;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	public Integer getAddedPoints() {
		return addedPoints;
	}

	public boolean isAddedAfterStart() {
		return addedAfterStart;
	}

	public Instant getRemovedAt() {
		return removedAt;
	}

	public RemovalReason getRemovalReason() {
		return removalReason;
	}

	public Integer getRemovedPoints() {
		return removedPoints;
	}

	public IssueStatus getFinalStatus() {
		return finalStatus;
	}

	public Integer getFinalPoints() {
		return finalPoints;
	}

}
