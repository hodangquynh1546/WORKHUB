package com.workhub.project;

import static com.workhub.access.ProjectPermission.COMMENT;
import static com.workhub.access.ProjectPermission.EDIT_DOCUMENTS;
import static com.workhub.access.ProjectPermission.EDIT_ISSUES;
import static com.workhub.access.ProjectPermission.MANAGE_BACKLOG;
import static com.workhub.access.ProjectPermission.MANAGE_MEETINGS;
import static com.workhub.access.ProjectPermission.MANAGE_SPRINTS;
import static com.workhub.access.ProjectPermission.VIEW;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.workhub.access.ProjectPermission;

/**
 * One role per member per project. PRODUCT_OWNER and SCRUM_MASTER are held by at most one member each
 * (Scrum accountabilities); a project always keeps at least one PROJECT_MANAGER.
 */
public enum ProjectRole {

	PROJECT_MANAGER(EnumSet.allOf(ProjectPermission.class)),
	PRODUCT_OWNER(EnumSet.of(VIEW, EDIT_DOCUMENTS, MANAGE_MEETINGS, MANAGE_BACKLOG, MANAGE_SPRINTS, EDIT_ISSUES, COMMENT)),
	SCRUM_MASTER(EnumSet.of(VIEW, EDIT_DOCUMENTS, MANAGE_MEETINGS, MANAGE_SPRINTS, EDIT_ISSUES, COMMENT)),
	DEVELOPER(EnumSet.of(VIEW, EDIT_DOCUMENTS, MANAGE_MEETINGS, EDIT_ISSUES, COMMENT)),
	QA_ENGINEER(EnumSet.of(VIEW, EDIT_DOCUMENTS, MANAGE_MEETINGS, EDIT_ISSUES, COMMENT)),
	STAKEHOLDER(EnumSet.of(VIEW, COMMENT));

	private final Set<ProjectPermission> permissions;

	ProjectRole(Set<ProjectPermission> permissions) {
		this.permissions = Collections.unmodifiableSet(permissions);
	}

	public Set<ProjectPermission> permissions() {
		return permissions;
	}

	/** Roles only one member of a project may hold. */
	boolean isSingleHolder() {
		return this == PRODUCT_OWNER || this == SCRUM_MASTER;
	}

}
