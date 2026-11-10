package com.workhub.access;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.team.Team;
import com.workhub.team.TeamRole;
import com.workhub.workspace.WorkspaceRole;

/** The caller's standing in a team. {@code teamRole} is null when they are not on the team. */
public record TeamAccess(Team team, UUID userId, WorkspaceRole workspaceRole, TeamRole teamRole) {

	private static final Set<TeamPermission> ALL = Collections.unmodifiableSet(EnumSet.allOf(TeamPermission.class));

	private static final Set<TeamPermission> VIEW_ONLY = Collections.unmodifiableSet(EnumSet.of(TeamPermission.VIEW));

	/**
	 * Workspace roles with {@link WorkspacePermission#MANAGE_TEAMS} act as lead of every team; otherwise the team
	 * role decides, and any other workspace member may view.
	 */
	public static Set<TeamPermission> permissionsFor(WorkspaceRole workspaceRole, TeamRole teamRole) {
		if (workspaceRole.permissions().contains(WorkspacePermission.MANAGE_TEAMS)) {
			return ALL;
		}
		return (teamRole != null) ? teamRole.permissions() : VIEW_ONLY;
	}

	public Set<TeamPermission> permissions() {
		return permissionsFor(workspaceRole, teamRole);
	}

	/** @throws ApiException 403 FORBIDDEN when the caller lacks the permission */
	public TeamAccess require(TeamPermission permission) {
		if (!permissions().contains(permission)) {
			throw ApiException.forbidden("FORBIDDEN", "Missing team permission " + permission);
		}
		return this;
	}

	/** @throws ApiException 409 TEAM_ARCHIVED when the team is archived (archived teams are read-only) */
	public TeamAccess requireActive() {
		if (team.isArchived()) {
			throw ApiException.conflict("TEAM_ARCHIVED", "Team is archived");
		}
		return this;
	}

}
