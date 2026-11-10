package com.workhub.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(max = 150) String fullName,
		@Size(max = 1024) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String avatarUrl) {
}
