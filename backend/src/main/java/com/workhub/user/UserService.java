package com.workhub.user;

import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.common.Strings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
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
		user.updateProfile(request.fullName().strip(), Strings.trimToNull(request.avatarUrl()));
		return UserProfile.from(user);
	}

}
