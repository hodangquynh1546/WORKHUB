package com.workhub.chat;

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

@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private ConversationKind kind;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "project_id", updatable = false)
	private UUID projectId;

	@Column(name = "team_id", updatable = false)
	private UUID teamId;

	@Column(length = 100)
	private String title;

	@Column(name = "direct_key", updatable = false, length = 80)
	private String directKey;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_message_at")
	private Instant lastMessageAt;

	protected Conversation() {
	}

	private Conversation(ConversationKind kind, UUID workspaceId, UUID createdBy) {
		this.kind = kind;
		this.workspaceId = workspaceId;
		this.createdBy = createdBy;
	}

	static Conversation project(UUID workspaceId, UUID projectId) {
		Conversation c = new Conversation(ConversationKind.PROJECT, workspaceId, null);
		c.projectId = projectId;
		return c;
	}

	static Conversation team(UUID workspaceId, UUID teamId) {
		Conversation c = new Conversation(ConversationKind.TEAM, workspaceId, null);
		c.teamId = teamId;
		return c;
	}

	static Conversation direct(UUID workspaceId, String key, UUID createdBy) {
		Conversation c = new Conversation(ConversationKind.DIRECT, workspaceId, createdBy);
		c.directKey = key;
		return c;
	}

	static Conversation group(UUID workspaceId, String title, UUID createdBy) {
		Conversation c = new Conversation(ConversationKind.GROUP, workspaceId, createdBy);
		c.title = title;
		return c;
	}

	void rename(String title) {
		this.title = title;
	}

	void touch(Instant at) {
		this.lastMessageAt = at;
	}

	public UUID getId() {
		return id;
	}

	public ConversationKind getKind() {
		return kind;
	}

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getTeamId() {
		return teamId;
	}

	public String getTitle() {
		return title;
	}

	public Instant getLastMessageAt() {
		return lastMessageAt;
	}

}
