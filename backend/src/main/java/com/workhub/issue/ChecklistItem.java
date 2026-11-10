package com.workhub.issue;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "issue_checklist_items")
public class ChecklistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "issue_id", nullable = false, updatable = false)
	private UUID issueId;

	@Column(nullable = false, length = 500)
	private String text;

	@Column(nullable = false)
	private boolean done;

	@Column(nullable = false)
	private int position;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ChecklistItem() {
	}

	ChecklistItem(UUID issueId, String text, int position) {
		this.issueId = issueId;
		this.text = text;
		this.position = position;
	}

	void setText(String text) {
		this.text = text;
	}

	void setDone(boolean done) {
		this.done = done;
	}

	public UUID getId() {
		return id;
	}

	public UUID getIssueId() {
		return issueId;
	}

	public String getText() {
		return text;
	}

	public boolean isDone() {
		return done;
	}

	public int getPosition() {
		return position;
	}

}
