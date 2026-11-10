package com.workhub.access;

import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.team.Team;
import com.workhub.team.TeamMember;
import com.workhub.team.TeamMemberRepository;
import com.workhub.team.TeamRepository;
import com.workhub.workspace.WorkspaceMember;
import com.workhub.workspace.WorkspaceMemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that resolves who may do what. Services call it first and then {@code require(...)} the
 * permission they need. Anything outside the caller's workspaces answers 404, so ids cannot be probed.
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

	private final WorkspaceMemberRepository workspaceMembers;

	private final TeamRepository teams;

	private final TeamMemberRepository teamMembers;

	public AccessService(WorkspaceMemberRepository workspaceMembers, TeamRepository teams,
			TeamMemberRepository teamMembers) {
		this.workspaceMembers = workspaceMembers;
		this.teams = teams;
		this.teamMembers = teamMembers;
	}

	/** @throws ApiException 404 WORKSPACE_NOT_FOUND when the caller is not a member */
	public WorkspaceAccess workspace(UUID userId, UUID workspaceId) {
		return workspaceMembers.findByWorkspaceIdAndUserId(workspaceId, userId)
			.map((member) -> new WorkspaceAccess(workspaceId, userId, member.getRole()))
			.orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "Workspace not found"));
	}

	/** @throws ApiException 404 TEAM_NOT_FOUND when the team does not exist or is outside the caller's workspaces */
	public TeamAccess team(UUID userId, UUID teamId) {
		ApiException notFound = ApiException.notFound("TEAM_NOT_FOUND", "Team not found");
		Team team = teams.findById(teamId).orElseThrow(() -> notFound);
		WorkspaceMember workspaceMember = workspaceMembers.findByWorkspaceIdAndUserId(team.getWorkspaceId(), userId)
			.orElseThrow(() -> notFound);
		return new TeamAccess(team, userId, workspaceMember.getRole(),
				teamMembers.findByTeamIdAndUserId(teamId, userId).map(TeamMember::getRole).orElse(null));
	}

}
