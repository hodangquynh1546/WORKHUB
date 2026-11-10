package com.workhub.board;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.workhub.issue.IssueStatus;

import java.util.UUID;

@Entity
@Table(name = "workflow_statuses")
public class WorkflowStatus {

	@EmbeddedId
	private WorkflowStatusKey key;

	@Column(nullable = false, length = 40)
	private String name;

	@Column(nullable = false)
	private boolean enabled;

	protected WorkflowStatus() {
	}

	WorkflowStatus(UUID projectId, IssueStatus status, String name, boolean enabled) {
		this.key = new WorkflowStatusKey(projectId, status);
		this.name = name;
		this.enabled = enabled;
	}

	void update(String name, boolean enabled) {
		this.name = name;
		this.enabled = enabled;
	}

}
