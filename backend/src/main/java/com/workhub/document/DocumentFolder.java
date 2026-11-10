package com.workhub.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "document_folders")
public class DocumentFolder {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "parent_id", updatable = false)
	private UUID parentId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentFolder() {
	}

	DocumentFolder(UUID projectId, UUID parentId, String name, UUID createdBy) {
		this.projectId = projectId;
		this.parentId = parentId;
		this.name = name;
		this.createdBy = createdBy;
	}

	void rename(String name) {
		this.name = name;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getParentId() {
		return parentId;
	}

	public String getName() {
		return name;
	}

}
