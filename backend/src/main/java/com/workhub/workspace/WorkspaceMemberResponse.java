package com.workhub.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(UUID userId, String email, String fullName, String avatarUrl,
		WorkspaceRole role, Instant joinedAt) {
}
