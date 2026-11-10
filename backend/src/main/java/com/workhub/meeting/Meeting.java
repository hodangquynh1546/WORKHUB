package com.workhub.meeting;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** A scheduled meeting of a project. Soft-deleted rows are invisible to every query. */
@Entity
@Table(name = "meetings")
@SQLRestriction("deleted_at is null")
public class Meeting {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "sprint_id")
	private UUID sprintId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "meeting_type", nullable = false, length = 32)
	private MeetingType type;

	@Column(name = "organizer_id", nullable = false)
	private UUID organizerId;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(length = 255)
	private String location;

	@Column(name = "online_link", length = 500)
	private String onlineLink;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "TEXT")
	private String agenda;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "TEXT")
	private String description;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(columnDefinition = "TEXT")
	private String minutes;

	@Column(name = "minutes_revision", nullable = false)
	private int minutesRevision;

	@Column(name = "reminder_minutes")
	private Integer reminderMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MeetingStatus status = MeetingStatus.SCHEDULED;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Meeting() {
	}

	Meeting(UUID projectId, UUID createdBy) {
		this.projectId = projectId;
		this.createdBy = createdBy;
	}

	void apply(MeetingRequests.Save request, UUID organizerId, String agenda, String description, String location,
			String onlineLink) {
		this.title = request.title().strip();
		this.type = request.type();
		this.sprintId = request.sprintId();
		this.organizerId = organizerId;
		this.startsAt = request.startsAt();
		this.endsAt = request.endsAt();
		this.location = location;
		this.onlineLink = onlineLink;
		this.agenda = agenda;
		this.description = description;
		this.reminderMinutes = request.reminderMinutes();
	}

	void setStatus(MeetingStatus status) {
		this.status = status;
	}

	void markDeleted(Instant now) {
		this.deletedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public String getTitle() {
		return title;
	}

	public MeetingType getType() {
		return type;
	}

	public UUID getOrganizerId() {
		return organizerId;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public String getLocation() {
		return location;
	}

	public String getOnlineLink() {
		return onlineLink;
	}

	public String getAgenda() {
		return agenda;
	}

	public String getDescription() {
		return description;
	}

	public String getMinutes() {
		return minutes;
	}

	public int getMinutesRevision() {
		return minutesRevision;
	}

	public Integer getReminderMinutes() {
		return reminderMinutes;
	}

	public MeetingStatus getStatus() {
		return status;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
