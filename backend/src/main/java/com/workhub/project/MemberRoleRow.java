package com.workhub.project;

import java.time.Instant;
import java.util.UUID;

/** One held role of one member, as read from the database. */
public record MemberRoleRow(UUID userId, String email, String fullName, String avatarUrl, ProjectRole role, Instant joinedAt) {
}
