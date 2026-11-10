package com.workhub.access;

/**
 * What a caller may do in one project; resolved by {@link ProjectAccess#permissionsFor}. The role mapping is in
 * {@link com.workhub.project.ProjectRole}. M3 enforces VIEW, UPDATE_PROJECT and MANAGE_MEMBERS; the rest are for
 * the backlog/sprint/issue modules.
 */
public enum ProjectPermission {

	/** See the project, its overview and members. */
	VIEW,
	/** Edit settings and change status (including archiving). */
	UPDATE_PROJECT,
	/** Add, remove and change roles of project members. */
	MANAGE_MEMBERS,
	/** Order and refine the product backlog. */
	MANAGE_BACKLOG,
	/** Plan, start and complete sprints. */
	MANAGE_SPRINTS,
	/** Create and update issues, log work. */
	EDIT_ISSUES,
	/** Comment on issues and documents. */
	COMMENT,
	/** Create and edit documents, folders, attachments and links; restore versions. */
	EDIT_DOCUMENTS

}
