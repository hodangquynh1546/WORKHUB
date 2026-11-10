package com.workhub.workspace;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.WorkspacePermission;

/** A workspace as seen by one member: their role and the permissions it grants (the UI uses them). */
public record WorkspaceResponse(UUID id, String name, String description, String logoUrl, Instant createdAt,
		WorkspaceRole myRole, Set<WorkspacePermission> permissions) {

	/** Used by the JPQL constructor expression in {@link WorkspaceRepository}. */
	public WorkspaceResponse(UUID id, String name, String description, String logoUrl, Instant createdAt,
			WorkspaceRole myRole) {
		this(id, name, description, logoUrl, createdAt, myRole, myRole.permissions());
	}

	static WorkspaceResponse of(Workspace workspace, WorkspaceRole myRole) {
		return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(),
				workspace.getLogoUrl(), workspace.getCreatedAt(), myRole);
	}

}
