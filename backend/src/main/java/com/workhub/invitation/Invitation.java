package com.workhub.invitation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Invitation into a workspace ({@code teamId == null}, {@code role} is a workspace role), into a team
 * ({@code role} is a team role) or into a project ({@code projectId} set, {@code role} is a project role); accepting
 * the last two also adds the user to the workspace as MEMBER.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "team_id", updatable = false)
	private UUID teamId;

	/** Set for an invitation into one project; {@code role} is then a project role. */
	@Column(name = "project_id", updatable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private InvitationKind kind;

	@Column(length = 320, updatable = false)
	private String email;

	@Column(nullable = false, length = 32, updatable = false)
	private String role;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private InvitationStatus status;

	@Column(name = "invited_by", nullable = false, updatable = false)
	private UUID invitedBy;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "responded_at")
	private Instant respondedAt;

	@Column(name = "responded_by")
	private UUID respondedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Invitation() {
	}

	Invitation(UUID workspaceId, UUID teamId, UUID projectId, InvitationKind kind, String email, String role,
			String tokenHash, UUID invitedBy, Instant expiresAt) {
		this.workspaceId = workspaceId;
		this.teamId = teamId;
		this.projectId = projectId;
		this.kind = kind;
		this.email = email;
		this.role = role;
		this.tokenHash = tokenHash;
		this.invitedBy = invitedBy;
		this.expiresAt = expiresAt;
		this.status = InvitationStatus.PENDING;
	}

	/** The stored status, or EXPIRED for a pending invitation past its expiry. */
	InvitationStatus statusAt(Instant now) {
		return (status == InvitationStatus.PENDING && !now.isBefore(expiresAt)) ? InvitationStatus.EXPIRED : status;
	}

	void respond(InvitationStatus outcome, UUID userId, Instant now) {
		this.status = outcome;
		this.respondedBy = userId;
		this.respondedAt = now;
	}

	void revoke() {
		this.status = InvitationStatus.REVOKED;
	}

	UUID getId() {
		return id;
	}

	UUID getWorkspaceId() {
		return workspaceId;
	}

	UUID getTeamId() {
		return teamId;
	}

	UUID getProjectId() {
		return projectId;
	}

	InvitationKind getKind() {
		return kind;
	}

	String getEmail() {
		return email;
	}

	String getRole() {
		return role;
	}

	InvitationStatus getStatus() {
		return status;
	}

}
