package com.workhub.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(UUID userId, String email, String fullName, String avatarUrl, ProjectRole role,
		java.util.List<ProjectRole> roles, Instant joinedAt) {
}
