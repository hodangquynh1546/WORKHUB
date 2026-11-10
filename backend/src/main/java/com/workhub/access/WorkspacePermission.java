package com.workhub.access;

/** What a workspace role allows. Mapping lives in {@link com.workhub.workspace.WorkspaceRole}. */
public enum WorkspacePermission {

	/** See the workspace, its members and its teams. */
	VIEW,
	/** Edit name, description, logo. */
	UPDATE_SETTINGS,
	/** Invite and remove plain members. */
	MANAGE_MEMBERS,
	/** Promote/demote admins, invite or remove admins. */
	MANAGE_ADMINS,
	/** Read the workspace's audit log and export it. */
	VIEW_AUDIT,
	/** Create teams and act as lead of every team. */
	MANAGE_TEAMS

}
