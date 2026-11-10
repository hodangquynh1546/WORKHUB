package com.workhub.meeting;

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

/** Something the meeting decided to do: an owner, a deadline, a status, and optionally the issue it became. */
@Entity
@Table(name = "meeting_action_items")
public class MeetingActionItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "meeting_id", nullable = false, updatable = false)
	private UUID meetingId;

	@Column(name = "item_text", nullable = false, length = 500)
	private String text;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ActionItemStatus status = ActionItemStatus.OPEN;

	@Column(name = "issue_id")
	private UUID issueId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MeetingActionItem() {
	}

	MeetingActionItem(UUID meetingId, UUID createdBy) {
		this.meetingId = meetingId;
		this.createdBy = createdBy;
	}

	void update(String text, UUID assigneeId, LocalDate dueDate, ActionItemStatus status) {
		this.text = text;
		this.assigneeId = assigneeId;
		this.dueDate = dueDate;
		this.status = status;
	}

	void convertedTo(UUID issueId) {
		this.issueId = issueId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMeetingId() {
		return meetingId;
	}

	public String getText() {
		return text;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public ActionItemStatus getStatus() {
		return status;
	}

	public UUID getIssueId() {
		return issueId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
