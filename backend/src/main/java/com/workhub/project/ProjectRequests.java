package com.workhub.project;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProjectRequests {

	private static final String URL = "^https?://\\S+$";

	private ProjectRequests() {
	}

	/**
	 * {@code key}: 2–10 letters/digits starting with a letter, stored upper-case, unique per workspace.
	 * {@code members}: optional initial members besides the creator, who always becomes PROJECT_MANAGER.
	 */
	public record Create(@NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
			message = "2-10 letters or digits, starting with a letter") String key,
			@NotBlank @Size(max = 100) String name, @Size(max = 2000) String description,
			LocalDate startDate, LocalDate endDate, @NotNull Methodology methodology,
			@Size(max = 100) List<@Valid AddMember> members) {
	}

	/** The key cannot change: future issue keys are built from it. */
	public record Update(@NotBlank @Size(max = 100) String name, @Size(max = 2000) String description,
			LocalDate startDate, LocalDate endDate, @NotNull Methodology methodology) {
	}

	public record ChangeStatus(@NotNull ProjectStatus status) {
	}

	public record AddMember(@NotNull UUID userId, @NotNull ProjectRole role) {
	}

	/** The member's complete set of roles (at least one). */
	public record ChangeRoles(@NotNull @jakarta.validation.constraints.Size(min = 1, max = 6) java.util.Set<@NotNull ProjectRole> roles) {
	}

}
