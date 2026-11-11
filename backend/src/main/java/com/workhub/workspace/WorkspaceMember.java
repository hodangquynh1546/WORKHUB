package com.workhub.workspace;

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

@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WorkspaceRole role;

	/** True for people who only came in through a team or project invitation. */
	@Column(nullable = false)
	private boolean guest;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected WorkspaceMember() {
	}

	public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
		this.workspaceId = workspaceId;
		this.userId = userId;
		this.role = role;
	}

	public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role, boolean guest) {
		this(workspaceId, userId, role);
		this.guest = guest;
	}

	public boolean isGuest() {
		return guest;
	}

	/** A workspace invitation turns a guest into a full member with the invited role. */
	public void becomeMember(WorkspaceRole role) {
		this.guest = false;
		this.role = role;
	}

	public WorkspaceRole getRole() {
		return role;
	}

	void changeRole(WorkspaceRole role) {
		this.role = role;
	}

}
