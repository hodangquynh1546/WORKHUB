package com.workhub.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/** One line of the audit log. Immutable: every column is insert-only. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", updatable = false)
	private UUID workspaceId;

	@Column(name = "project_id", updatable = false)
	private UUID projectId;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "actor_name", updatable = false, length = 150)
	private String actorName;

	@Column(nullable = false, updatable = false, length = 60)
	private String action;

	@Column(name = "target_type", updatable = false, length = 30)
	private String targetType;

	@Column(name = "target_id", updatable = false)
	private UUID targetId;

	@Column(name = "target_label", updatable = false, length = 300)
	private String targetLabel;

	@Column(updatable = false, length = 500)
	private String detail;

	@Column(nullable = false, updatable = false, length = 10)
	private String outcome;

	@Column(updatable = false, length = 64)
	private String ip;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditEntry() {
	}

	AuditEntry(UUID workspaceId, UUID projectId, UUID actorId, String actorName, String action, String targetType,
			UUID targetId, String targetLabel, String detail, String outcome, String ip) {
		this.workspaceId = workspaceId;
		this.projectId = projectId;
		this.actorId = actorId;
		this.actorName = cut(actorName, 150);
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.targetLabel = cut(targetLabel, 300);
		this.detail = cut(detail, 500);
		this.outcome = outcome;
		this.ip = cut(ip, 64);
	}

	private static String cut(String text, int max) {
		return (text == null || text.length() <= max) ? text : text.substring(0, max);
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getActorId() {
		return actorId;
	}

	public String getActorName() {
		return actorName;
	}

	public String getAction() {
		return action;
	}

	public String getTargetType() {
		return targetType;
	}

	public UUID getTargetId() {
		return targetId;
	}

	public String getTargetLabel() {
		return targetLabel;
	}

	public String getDetail() {
		return detail;
	}

	public String getOutcome() {
		return outcome;
	}

	public String getIp() {
		return ip;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
