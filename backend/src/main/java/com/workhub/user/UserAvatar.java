package com.workhub.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An uploaded profile picture (already downscaled by the browser). */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false)
	private byte[] data;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserAvatar() {
	}

	public UserAvatar(UUID userId, String contentType, byte[] data, Instant updatedAt) {
		this.userId = userId;
		this.contentType = contentType;
		this.data = data;
		this.updatedAt = updatedAt;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getData() {
		return data;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
