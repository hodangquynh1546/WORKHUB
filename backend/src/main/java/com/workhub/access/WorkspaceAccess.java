package com.workhub.access;

import java.util.Set;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.workspace.WorkspaceRole;

/** The caller's standing in a workspace they are a member of. */
public record WorkspaceAccess(UUID workspaceId, UUID userId, WorkspaceRole role) {

	public Set<WorkspacePermission> permissions() {
		return role.permissions();
	}

	public boolean has(WorkspacePermission permission) {
		return role.permissions().contains(permission);
	}

	/** @throws ApiException 403 FORBIDDEN when the caller lacks the permission */
	public WorkspaceAccess require(WorkspacePermission permission) {
		if (!has(permission)) {
			throw ApiException.forbidden("FORBIDDEN", "Missing workspace permission " + permission);
		}
		return this;
	}

}
