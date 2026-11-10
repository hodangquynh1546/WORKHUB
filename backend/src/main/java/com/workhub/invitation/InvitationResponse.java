package com.workhub.invitation;

import java.time.Instant;
import java.util.UUID;

/** {@code teamId}/{@code teamName} are null for a workspace invitation; {@code role} is a workspace or team role. */
public record InvitationResponse(UUID id, InvitationKind kind, String email, UUID workspaceId, String workspaceName,
		UUID teamId, String teamName, String role, String invitedByName, InvitationStatus status, Instant expiresAt,
		Instant createdAt) {

	InvitationResponse withStatus(InvitationStatus status) {
		return new InvitationResponse(id, kind, email, workspaceId, workspaceName, teamId, teamName, role,
				invitedByName, status, expiresAt, createdAt);
	}

}
