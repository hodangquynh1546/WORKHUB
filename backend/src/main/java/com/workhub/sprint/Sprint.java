package com.workhub.sprint;

import java.time.Instant;
import java.time.LocalDate;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sprints")
public class Sprint {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 1000)
	private String goal;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SprintStatus status;

	@Column(name = "facilitator_id")
	private UUID facilitatorId;

	@Column(name = "capacity_points")
	private Integer capacityPoints;

	@Column(name = "committed_points")
	private Integer committedPoints;

	@Column(name = "committed_issues")
	private Integer committedIssues;

	@Column(name = "completed_points")
	private Integer completedPoints;

	@Column(name = "completed_issues")
	private Integer completedIssues;

	@Enumerated(EnumType.STRING)
	@Column(name = "goal_outcome", length = 24)
	private GoalOutcome goalOutcome;

	@Column(name = "goal_note", length = 1000)
	private String goalNote;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Sprint() {
	}

	Sprint(UUID projectId, UUID createdBy) {
		this.projectId = projectId;
		this.createdBy = createdBy;
		this.status = SprintStatus.FUTURE;
	}

	void setDetails(String name, String goal, LocalDate startDate, LocalDate endDate, UUID facilitatorId,
			Integer capacityPoints) {
		this.name = name;
		this.goal = goal;
		this.startDate = startDate;
		this.endDate = endDate;
		this.facilitatorId = facilitatorId;
		this.capacityPoints = capacityPoints;
	}

	void start(Instant now, int issues, int points) {
		this.status = SprintStatus.ACTIVE;
		this.startedAt = now;
		this.committedIssues = issues;
		this.committedPoints = points;
	}

	void complete(Instant now, int doneIssues, int donePoints, GoalOutcome outcome, String note) {
		this.status = SprintStatus.COMPLETED;
		this.completedAt = now;
		this.completedIssues = doneIssues;
		this.completedPoints = donePoints;
		this.goalOutcome = outcome;
		this.goalNote = note;
	}

	void cancel(Instant now) {
		this.status = SprintStatus.CANCELLED;
		this.completedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getGoal() {
		return goal;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public SprintStatus getStatus() {
		return status;
	}

	public UUID getFacilitatorId() {
		return facilitatorId;
	}

	public Integer getCapacityPoints() {
		return capacityPoints;
	}

	public Integer getCommittedPoints() {
		return committedPoints;
	}

	public Integer getCommittedIssues() {
		return committedIssues;
	}

	public Integer getCompletedPoints() {
		return completedPoints;
	}

	public Integer getCompletedIssues() {
		return completedIssues;
	}

	public GoalOutcome getGoalOutcome() {
		return goalOutcome;
	}

	public String getGoalNote() {
		return goalNote;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
