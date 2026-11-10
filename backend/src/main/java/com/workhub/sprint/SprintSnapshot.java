package com.workhub.sprint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The state of an active sprint at the end of one calendar day (the last write of the day wins). */
@Entity
@Table(name = "sprint_snapshots")
public class SprintSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "sprint_id", nullable = false, updatable = false)
	private UUID sprintId;

	@Column(name = "snapshot_date", nullable = false, updatable = false)
	private LocalDate date;

	@Column(name = "scope_points", nullable = false)
	private int scopePoints;

	@Column(name = "done_points", nullable = false)
	private int donePoints;

	@Column(name = "scope_issues", nullable = false)
	private int scopeIssues;

	@Column(name = "todo_issues", nullable = false)
	private int todoIssues;

	@Column(name = "in_progress_issues", nullable = false)
	private int inProgressIssues;

	@Column(name = "done_issues", nullable = false)
	private int doneIssues;

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	protected SprintSnapshot() {
	}

	SprintSnapshot(UUID sprintId, LocalDate date) {
		this.sprintId = sprintId;
		this.date = date;
	}

	void update(SprintCounts counts, Instant now) {
		this.scopePoints = counts.scopePoints();
		this.donePoints = counts.donePoints();
		this.scopeIssues = counts.issues();
		this.todoIssues = counts.todo();
		this.inProgressIssues = counts.inProgress();
		this.doneIssues = counts.done();
		this.capturedAt = now;
	}

	public LocalDate getDate() {
		return date;
	}

	public int getScopePoints() {
		return scopePoints;
	}

	public int getDonePoints() {
		return donePoints;
	}

	public int getScopeIssues() {
		return scopeIssues;
	}

	public int getTodoIssues() {
		return todoIssues;
	}

	public int getInProgressIssues() {
		return inProgressIssues;
	}

	public int getDoneIssues() {
		return doneIssues;
	}

	public Instant getCapturedAt() {
		return capturedAt;
	}

}
