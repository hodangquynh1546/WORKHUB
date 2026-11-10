package com.workhub.team;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(UUID userId, String email, String fullName, String avatarUrl, TeamRole role,
		Instant joinedAt) {
}
