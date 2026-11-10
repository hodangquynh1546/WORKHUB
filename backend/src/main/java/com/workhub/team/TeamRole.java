package com.workhub.team;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.workhub.access.TeamPermission;

/** Role inside one team. Workspace owners/admins additionally act as lead of every team. */
public enum TeamRole {

	LEAD(EnumSet.allOf(TeamPermission.class)),
	MEMBER(EnumSet.of(TeamPermission.VIEW));

	private final Set<TeamPermission> permissions;

	TeamRole(Set<TeamPermission> permissions) {
		this.permissions = Collections.unmodifiableSet(permissions);
	}

	public Set<TeamPermission> permissions() {
		return permissions;
	}

}
