package com.workhub.auth;

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
@Table(name = "user_tokens")
public class UserToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TokenType type;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserToken() {
	}

	UserToken(UUID userId, TokenType type, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.type = type;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getUserId() {
		return userId;
	}

	boolean isConsumed() {
		return consumedAt != null;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}

	void revoke(Instant now) {
		this.revokedAt = now;
	}

}
