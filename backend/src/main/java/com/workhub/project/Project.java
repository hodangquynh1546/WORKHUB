package com.workhub.project;

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
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "team_id", nullable = false, updatable = false)
	private UUID teamId;

	/** Not called {@code key}: that is a JPQL function name. */
	@Column(name = "project_key", nullable = false, updatable = false, length = 10)
	private String projectKey;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 2000)
	private String description;

	@Column(name = "avatar_url", length = 1024)
	private String avatarUrl;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Methodology methodology;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ProjectStatus status;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Project() {
	}

	Project(UUID workspaceId, UUID teamId, String projectKey, ProjectDetails details, UUID createdBy) {
		this.workspaceId = workspaceId;
		this.teamId = teamId;
		this.projectKey = projectKey;
		this.createdBy = createdBy;
		this.status = ProjectStatus.PLANNING;
		update(details);
	}

	void update(ProjectDetails details) {
		this.name = details.name();
		this.description = details.description();
		this.avatarUrl = details.avatarUrl();
		this.startDate = details.startDate();
		this.endDate = details.endDate();
		this.methodology = details.methodology();
	}

	void changeStatus(ProjectStatus status) {
		this.status = status;
	}

	public boolean isArchived() {
		return status == ProjectStatus.ARCHIVED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public UUID getTeamId() {
		return teamId;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public Methodology getMethodology() {
		return methodology;
	}

	public ProjectStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
