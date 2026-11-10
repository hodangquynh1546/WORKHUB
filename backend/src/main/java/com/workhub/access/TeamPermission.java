package com.workhub.access;

/** What a caller may do in one team; resolved by {@link TeamAccess#permissionsFor}. */
public enum TeamPermission {

	/** See the team and its members (every workspace member can). */
	VIEW,
	/** Edit, archive and unarchive the team. */
	UPDATE_TEAM,
	/** Add, remove, change roles of and invite team members. */
	MANAGE_MEMBERS,
	/** Create projects in the team and act as project manager of all of them. */
	MANAGE_PROJECTS

}
