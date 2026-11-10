package com.workhub.workspace;

/** A user's role within one workspace (roles are always scoped, never global). */
public enum WorkspaceRole {

	OWNER, ADMIN, MEMBER;

	/** May change workspace settings. */
	boolean canManage() {
		return this == OWNER || this == ADMIN;
	}

}
