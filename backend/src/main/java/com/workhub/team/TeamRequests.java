package com.workhub.team;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TeamRequests {

	private TeamRequests() {
	}

	/** Create or update a team. */
	public record Save(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description) {
	}

	/** Add an existing workspace member to the team. */
	public record AddMember(@NotNull UUID userId, @NotNull TeamRole role) {
	}

	public record ChangeRole(@NotNull TeamRole role) {
	}

}
