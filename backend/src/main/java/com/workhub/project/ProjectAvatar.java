package com.workhub.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An uploaded project picture (already downscaled by the browser). */
@Entity
@Table(name = "project_avatars")
public class ProjectAvatar {

	@Id
	@Column(name = "project_id")
	private UUID projectId;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false)
	private byte[] data;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProjectAvatar() {
	}

	public ProjectAvatar(UUID projectId, String contentType, byte[] data, Instant updatedAt) {
		this.projectId = projectId;
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

}
