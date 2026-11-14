package com.workhub.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "avatar_url", length = 1024)
	private String avatarUrl;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String email, String passwordHash, String fullName) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.passwordChangedAt = Instant.now();
	}

	/** Emails are compared case-insensitively, so they are stored lower-cased. */
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public void rename(String fullName) {
		this.fullName = fullName;
	}

	public void changeAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}

	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = Instant.now();
	}

	/** Adopts a password changed in the other system, keeping the moment it was changed there. */
	public void adoptPasswordHash(String passwordHash, Instant changedAt) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = (changedAt != null) ? changedAt : Instant.now();
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	public void markEmailVerified() {
		this.emailVerified = true;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
