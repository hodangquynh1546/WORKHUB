package com.workhub.notification;

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
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private NotificationType type;

	@Column(nullable = false, updatable = false, length = 300)
	private String title;

	@Column(updatable = false, length = 1000)
	private String body;

	@Column(updatable = false, length = 300)
	private String link;

	@Column(name = "project_id", updatable = false)
	private UUID projectId;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "dedupe_key", updatable = false, length = 200)
	private String dedupeKey;

	@Column(name = "read_at")
	private Instant readAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Notification() {
	}

	Notification(UUID userId, NotificationType type, String title, String body, String link, UUID projectId,
			UUID actorId, String dedupeKey) {
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.link = link;
		this.projectId = projectId;
		this.actorId = actorId;
		this.dedupeKey = dedupeKey;
	}

	void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public String getLink() {
		return link;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getActorId() {
		return actorId;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
