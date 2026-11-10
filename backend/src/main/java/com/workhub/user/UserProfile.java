package com.workhub.user;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID id, String email, String fullName, String avatarUrl, boolean emailVerified,
		Instant createdAt) {

	public static UserProfile from(User user) {
		return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
				user.isEmailVerified(), user.getCreatedAt());
	}

}
