package com.workhub.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workhub.team.TeamRole;

/** Query projection behind project lists; filtered and turned into {@link ProjectResponse} once access is known. */
public record ProjectRow(UUID id, UUID workspaceId, UUID teamId, String teamName, String key, String name,
		String description, String avatarUrl, LocalDate startDate, LocalDate endDate, Methodology methodology,
		ProjectStatus status, Instant createdAt, long memberCount, TeamRole myTeamRole, UUID createdBy) {
}
