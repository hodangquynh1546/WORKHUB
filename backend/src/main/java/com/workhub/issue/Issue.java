package com.workhub.issue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/** A work item. Soft-deleted rows are invisible to every query ({@link SQLRestriction}). */
@Entity
@Table(name = "issues")
@SQLRestriction("deleted_at is null")
public class Issue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "issue_number", nullable = false, updatable = false)
	private int number;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IssueType type;

	@Column(nullable = false)
	private String title;

	@Column(length = 10000)
	private String description;

	@Column(name = "parent_id")
	private UUID parentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IssuePriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IssueStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RefinementStatus refinement;

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private UUID reporterId;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "story_points")
	private Integer storyPoints;

	@Column(name = "original_estimate_min")
	private Integer originalEstimateMinutes;

	@Column(name = "remaining_estimate_min")
	private Integer remainingEstimateMinutes;

	@Column(name = "acceptance_criteria", length = 10000)
	private String acceptanceCriteria;

	/** The sprint the issue belongs to (see V7); changed through {@link #moveToSprint}. */
	@Column(name = "sprint_id")
	private UUID sprintId;

	@Column(nullable = false)
	private boolean blocked;

	@Column(name = "blocked_reason", length = 500)
	private String blockedReason;

	@Column(name = "backlog_rank", nullable = false)
	private long backlogRank;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "done_at")
	private Instant doneAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "issue_labels", joinColumns = @JoinColumn(name = "issue_id"))
	@Column(name = "label", length = 30)
	@BatchSize(size = 100)
	private Set<String> labels = new LinkedHashSet<>();

	protected Issue() {
	}

	Issue(UUID projectId, int number, UUID reporterId, long backlogRank) {
		this.projectId = projectId;
		this.number = number;
		this.reporterId = reporterId;
		this.backlogRank = backlogRank;
		this.status = IssueStatus.TO_DO;
		this.refinement = RefinementStatus.NEW;
	}

	// ---- editable fields (validation lives in IssueService) ----

	void setType(IssueType type) {
		this.type = type;
	}

	void setTitle(String title) {
		this.title = title;
	}

	void setDescription(String description) {
		this.description = description;
	}

	void setParentId(UUID parentId) {
		this.parentId = parentId;
	}

	void setPriority(IssuePriority priority) {
		this.priority = priority;
	}

	void setRefinement(RefinementStatus refinement) {
		this.refinement = refinement;
	}

	void setAssigneeId(UUID assigneeId) {
		this.assigneeId = assigneeId;
	}

	void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	void setStoryPoints(Integer storyPoints) {
		this.storyPoints = storyPoints;
	}

	void setOriginalEstimateMinutes(Integer minutes) {
		this.originalEstimateMinutes = minutes;
	}

	void setRemainingEstimateMinutes(Integer minutes) {
		this.remainingEstimateMinutes = minutes;
	}

	void setAcceptanceCriteria(String acceptanceCriteria) {
		this.acceptanceCriteria = acceptanceCriteria;
	}

	void setBlocked(boolean blocked, String reason) {
		this.blocked = blocked;
		this.blockedReason = blocked ? reason : null;
	}

	/** Called by the sprint module only; null takes the issue out of every sprint. */
	public void moveToSprint(UUID sprintId) {
		this.sprintId = sprintId;
	}

	void setBacklogRank(long backlogRank) {
		this.backlogRank = backlogRank;
	}

	void replaceLabels(Set<String> newLabels) {
		labels.retainAll(newLabels);
		labels.addAll(newLabels);
	}

	/** Moves the issue through the workflow and keeps the flow timestamps in step. */
	void changeStatus(IssueStatus newStatus, Instant now) {
		if (startedAt == null && newStatus != IssueStatus.TO_DO) {
			startedAt = now;
		}
		doneAt = (newStatus == IssueStatus.DONE) ? now : null;
		this.status = newStatus;
	}

	void markDeleted(Instant now) {
		this.deletedAt = now;
	}

	// ---- read access ----

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public int getNumber() {
		return number;
	}

	public IssueType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public UUID getParentId() {
		return parentId;
	}

	public IssuePriority getPriority() {
		return priority;
	}

	public IssueStatus getStatus() {
		return status;
	}

	public RefinementStatus getRefinement() {
		return refinement;
	}

	public UUID getReporterId() {
		return reporterId;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public Integer getStoryPoints() {
		return storyPoints;
	}

	public Integer getOriginalEstimateMinutes() {
		return originalEstimateMinutes;
	}

	public Integer getRemainingEstimateMinutes() {
		return remainingEstimateMinutes;
	}

	public String getAcceptanceCriteria() {
		return acceptanceCriteria;
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public String getBlockedReason() {
		return blockedReason;
	}

	public long getBacklogRank() {
		return backlogRank;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getDoneAt() {
		return doneAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Set<String> getLabels() {
		return labels;
	}

}
