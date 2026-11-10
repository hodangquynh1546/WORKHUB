package com.workhub.workspace;

import java.time.Instant;
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
@Table(name = "workspaces")
public class Workspace {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "logo_url", length = 1024)
	private String logoUrl;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Workspace() {
	}

	Workspace(String name, String description, String logoUrl, UUID createdBy) {
		update(name, description, logoUrl);
		this.createdBy = createdBy;
	}

	void update(String name, String description, String logoUrl) {
		this.name = name;
		this.description = description;
		this.logoUrl = logoUrl;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
