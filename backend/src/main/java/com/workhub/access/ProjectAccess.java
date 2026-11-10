package com.workhub.access;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.project.Project;
import com.workhub.project.ProjectRole;
import com.workhub.team.TeamRole;
import com.workhub.workspace.WorkspaceRole;

/**
 * The caller's standing in a project. {@code teamRole} is their role in the owning team and {@code projectRole}
 * their role in the project; either may be null.
 */
public record ProjectAccess(Project project, UUID userId, WorkspaceRole workspaceRole, TeamRole teamRole,
		ProjectRole projectRole) {

	private static final Set<ProjectPermission> ALL = Collections
		.unmodifiableSet(EnumSet.allOf(ProjectPermission.class));

	private static final Set<ProjectPermission> VIEW_ONLY = Collections
		.unmodifiableSet(EnumSet.of(ProjectPermission.VIEW));

	/**
	 * Whoever manages the owning team (its lead, workspace owners/admins) manages its projects; otherwise the
	 * project role decides; other members of the owning team may view. Anyone else gets an empty set: no access.
	 */
	public static Set<ProjectPermission> permissionsFor(WorkspaceRole workspaceRole, TeamRole teamRole,
			ProjectRole projectRole) {
		if (TeamAccess.permissionsFor(workspaceRole, teamRole).contains(TeamPermission.MANAGE_PROJECTS)) {
			return ALL;
		}
		if (projectRole != null) {
			return projectRole.permissions();
		}
		return (teamRole != null) ? VIEW_ONLY : Set.of();
	}

	public Set<ProjectPermission> permissions() {
		return permissionsFor(workspaceRole, teamRole, projectRole);
	}

	/** @throws ApiException 403 FORBIDDEN when the caller lacks the permission */
	public ProjectAccess require(ProjectPermission permission) {
		if (!permissions().contains(permission)) {
			throw ApiException.forbidden("FORBIDDEN", "Missing project permission " + permission);
		}
		return this;
	}

	/** @throws ApiException 409 PROJECT_ARCHIVED when the project is archived (read-only until its status changes) */
	public ProjectAccess requireActive() {
		if (project.isArchived()) {
			throw ApiException.conflict("PROJECT_ARCHIVED", "Project is archived");
		}
		return this;
	}

}
