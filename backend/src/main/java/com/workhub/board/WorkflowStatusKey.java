package com.workhub.board;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.workhub.issue.IssueStatus;

@Embeddable
public class WorkflowStatusKey implements Serializable {

	@Column(name = "project_id")
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 16)
	private IssueStatus status;

	protected WorkflowStatusKey() {
	}

	WorkflowStatusKey(UUID projectId, IssueStatus status) {
		this.projectId = projectId;
		this.status = status;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof WorkflowStatusKey key && Objects.equals(projectId, key.projectId)
				&& status == key.status;
	}

	@Override
	public int hashCode() {
		return Objects.hash(projectId, status);
	}

}
