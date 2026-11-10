package com.workhub.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.workhub.common.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

	private static final int MAX_AVATAR_BYTES = 512 * 1024;

	private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final UserRepository users;

	private final UserAvatarRepository avatars;

	private final Clock clock;

	public UserService(UserRepository users, UserAvatarRepository avatars, Clock clock) {
		this.users = users;
		this.avatars = avatars;
		this.clock = clock;
	}

	public User require(UUID id) {
		return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
	}

	@Transactional(readOnly = true)
	public UserProfile getProfile(UUID id) {
		return UserProfile.from(require(id));
	}

	public UserProfile updateProfile(UUID id, UpdateProfileRequest request) {
		User user = require(id);
		user.rename(request.fullName().strip());
		return UserProfile.from(user);
	}

	/** Stores the picture and points {@code avatarUrl} at it; the version in the URL busts browser caches. */
	public UserProfile uploadAvatar(UUID id, String contentType, byte[] data) {
		if (data.length == 0 || data.length > MAX_AVATAR_BYTES) {
			throw ApiException.badRequest("AVATAR_TOO_LARGE", "Avatar must be between 1 byte and 512 KB");
		}
		if (contentType == null || !AVATAR_TYPES.contains(contentType) || !matchesType(contentType, data)) {
			throw ApiException.badRequest("AVATAR_INVALID_TYPE", "Avatar must be a JPEG, PNG or WebP image");
		}
		User user = require(id);
		Instant now = Instant.now(clock);
		avatars.save(new UserAvatar(id, contentType, data, now));
		user.changeAvatarUrl("/api/public/users/" + id + "/avatar?v=" + now.toEpochMilli());
		return UserProfile.from(user);
	}

	public UserProfile removeAvatar(UUID id) {
		User user = require(id);
		avatars.deleteById(id);
		user.changeAvatarUrl(null);
		return UserProfile.from(user);
	}

	@Transactional(readOnly = true)
	public UserAvatar avatar(UUID id) {
		return avatars.findById(id).orElseThrow(() -> ApiException.notFound("AVATAR_NOT_FOUND", "No avatar"));
	}

	/** The declared type is client-supplied, so also check the file's magic bytes. */
	private static boolean matchesType(String contentType, byte[] d) {
		return switch (contentType) {
			case "image/jpeg" -> d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
			case "image/png" -> d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
			case "image/webp" -> d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W'
					&& d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
			default -> false;
		};
	}

}
