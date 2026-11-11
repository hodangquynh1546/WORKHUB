package com.workhub.team;

import java.time.Instant;
import java.util.UUID;

/** Query projection behind the team list; turned into {@link TeamResponse} once permissions are known. */
public record TeamRow(UUID id, UUID workspaceId, String name, String description, Instant archivedAt,
		Instant createdAt, long memberCount, TeamRole myRole, UUID createdBy) {
}
