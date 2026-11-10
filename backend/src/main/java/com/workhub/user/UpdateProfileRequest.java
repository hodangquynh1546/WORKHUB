package com.workhub.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The profile picture is changed separately through {@code /api/me/avatar}. */
public record UpdateProfileRequest(@NotBlank @Size(max = 150) String fullName) {
}
