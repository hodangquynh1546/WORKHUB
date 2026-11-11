package com.workhub.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies of the auth endpoints. Password limits match {@link AuthService#checkPassword}. */
public final class AuthRequests {

	private AuthRequests() {
	}

	public record Register(@NotBlank @Email @Size(max = 320) String email,
			@NotNull @Size(min = 8, max = 72) String password, @NotBlank @Size(max = 150) String fullName) {
	}

	public record Login(@NotBlank String email, @NotNull String password) {
	}

	public record VerifyEmail(@NotBlank String token) {
	}

	public record ForgotPassword(@NotBlank @Email String email) {
	}

	public record ResetPassword(@NotBlank String token, @NotNull @Size(min = 8, max = 72) String newPassword) {
	}

	public record DeleteAccount(@NotNull String password) {
	}

	public record ChangePassword(@NotNull String currentPassword,
			@NotNull @Size(min = 8, max = 72) String newPassword) {
	}

}
