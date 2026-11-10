package com.workhub.team;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.TeamPermission;

/** A team as seen by the caller: {@code myRole} is null when they are not on it; {@code permissions} drive the UI. */
public record TeamResponse(UUID id, UUID workspaceId, String name, String description, boolean archived,
		Instant createdAt, long memberCount, TeamRole myRole, Set<TeamPermission> permissions) {
}
