package com.workhub.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.ProjectPermission;

/** A project as seen by the caller ({@code mine}: they created it): {@code myRole} is null when they are not a member; {@code permissions} drive the UI. */
public record ProjectResponse(UUID id, UUID workspaceId, UUID teamId, String teamName, String key, String name,
		String description, String avatarUrl, LocalDate startDate, LocalDate endDate, Methodology methodology,
		ProjectStatus status, Instant createdAt, long memberCount, ProjectRole myRole,
		java.util.List<ProjectRole> myRoles, Set<ProjectPermission> permissions, boolean mine) {

	static ProjectResponse of(ProjectRow row, UUID userId, java.util.Collection<ProjectRole> roles, Set<ProjectPermission> permissions) {
		return new ProjectResponse(row.id(), row.workspaceId(), row.teamId(), row.teamName(), row.key(), row.name(),
				row.description(), row.avatarUrl(), row.startDate(), row.endDate(), row.methodology(), row.status(),
				row.createdAt(), row.memberCount(), ProjectRole.primary(roles), sorted(roles), permissions, userId.equals(row.createdBy()));
	}

	static ProjectResponse of(Project project, UUID userId, String teamName, long memberCount, java.util.Collection<ProjectRole> roles,
			Set<ProjectPermission> permissions) {
		return new ProjectResponse(project.getId(), project.getWorkspaceId(), project.getTeamId(), teamName,
				project.getProjectKey(), project.getName(), project.getDescription(), project.getAvatarUrl(),
				project.getStartDate(), project.getEndDate(), project.getMethodology(), project.getStatus(),
				project.getCreatedAt(), memberCount, ProjectRole.primary(roles), sorted(roles), permissions, userId.equals(project.getCreatedBy()));
	}


	private static java.util.List<ProjectRole> sorted(java.util.Collection<ProjectRole> roles) {
		return roles.stream().sorted().toList();
	}

}
