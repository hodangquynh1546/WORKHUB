package com.workhub.invitation;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class InvitationRequests {

	private InvitationRequests() {
	}

	/**
	 * {@code email} is required for EMAIL and ignored for LINK. {@code role}: ADMIN or MEMBER for a workspace
	 * invitation, LEAD or MEMBER for a team invitation, a project role for a project invitation.
	 */
	public record Create(@NotNull InvitationKind kind, @Email @Size(max = 320) String email, @NotBlank String role) {
	}

	public record ByToken(@NotBlank String token) {
	}

	/** Returned once at creation: the raw link is not stored and cannot be shown again. */
	public record Created(InvitationResponse invitation, String inviteUrl) {
	}

	/** Where the user landed after accepting. */
	public record Accepted(UUID workspaceId, UUID teamId, UUID projectId) {
	}

}
