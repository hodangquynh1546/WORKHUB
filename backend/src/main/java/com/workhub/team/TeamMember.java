package com.workhub.team;

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
@Table(name = "team_members")
public class TeamMember {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "team_id", nullable = false, updatable = false)
	private UUID teamId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TeamRole role;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected TeamMember() {
	}

	public TeamMember(UUID teamId, UUID userId, TeamRole role) {
		this.teamId = teamId;
		this.userId = userId;
		this.role = role;
	}

	public TeamRole getRole() {
		return role;
	}

	void changeRole(TeamRole role) {
		this.role = role;
	}

}
