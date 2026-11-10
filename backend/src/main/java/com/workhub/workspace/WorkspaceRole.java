package com.workhub.workspace;

import static com.workhub.access.WorkspacePermission.MANAGE_ADMINS;
import static com.workhub.access.WorkspacePermission.MANAGE_MEMBERS;
import static com.workhub.access.WorkspacePermission.MANAGE_TEAMS;
import static com.workhub.access.WorkspacePermission.UPDATE_SETTINGS;
import static com.workhub.access.WorkspacePermission.VIEW;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.workhub.access.WorkspacePermission;

/** A user's role within one workspace (roles are always scoped, never global). Exactly one OWNER per workspace. */
public enum WorkspaceRole {

	OWNER(EnumSet.allOf(WorkspacePermission.class)),
	ADMIN(EnumSet.of(VIEW, UPDATE_SETTINGS, MANAGE_MEMBERS, MANAGE_TEAMS)),
	MEMBER(EnumSet.of(VIEW));

	private final Set<WorkspacePermission> permissions;

	WorkspaceRole(Set<WorkspacePermission> permissions) {
		this.permissions = Collections.unmodifiableSet(permissions);
	}

	public Set<WorkspacePermission> permissions() {
		return permissions;
	}

}
