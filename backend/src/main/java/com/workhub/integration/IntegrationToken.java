package com.workhub.integration;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A revocable credential for an external system acting as a user. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "integration_tokens")
public class IntegrationToken {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected IntegrationToken() {
	}

	IntegrationToken(UUID userId, String name, String tokenHash, Instant now) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.name = name;
		this.tokenHash = tokenHash;
		this.createdAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	void used(Instant now) {
		this.lastUsedAt = now;
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}

}
