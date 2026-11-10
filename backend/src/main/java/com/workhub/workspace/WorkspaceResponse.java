package com.workhub.workspace;

import java.time.Instant;
import java.util.UUID;

/** A workspace as seen by one member; {@code myRole} is that member's role. */
public record WorkspaceResponse(UUID id, String name, String description, String logoUrl, Instant createdAt,
		WorkspaceRole myRole) {

	static WorkspaceResponse of(Workspace workspace, WorkspaceRole myRole) {
		return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(),
				workspace.getLogoUrl(), workspace.getCreatedAt(), myRole);
	}

}
