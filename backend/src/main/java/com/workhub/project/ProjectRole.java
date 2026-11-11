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
 * A member may hold several roles in a project (their permissions add up). PROJECT_MANAGER and SCRUM_MASTER are held
 * by at most one member each; a project always keeps its PROJECT_MANAGER.
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
	public boolean isSingleHolder() {
		return this == PROJECT_MANAGER || this == SCRUM_MASTER;
	}

	/** The role shown first when someone holds several (the declaration order above, most powerful first). */
	public static ProjectRole primary(java.util.Collection<ProjectRole> roles) {
		return roles.stream().min(java.util.Comparator.naturalOrder()).orElse(null);
	}

}
