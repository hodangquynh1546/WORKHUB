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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "issue_comments")
public class IssueComment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "issue_id", nullable = false, updatable = false)
	private UUID issueId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(nullable = false, length = 5000)
	private String body;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected IssueComment() {
	}

	IssueComment(UUID issueId, UUID authorId, String body) {
		this.issueId = issueId;
		this.authorId = authorId;
		this.body = body;
	}

	void setBody(String body) {
		this.body = body;
	}

	public UUID getId() {
		return id;
	}

	public UUID getIssueId() {
		return issueId;
	}

	public UUID getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
