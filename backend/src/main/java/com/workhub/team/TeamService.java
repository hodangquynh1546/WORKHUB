package com.workhub.team;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.TeamAccess;
import com.workhub.access.TeamPermission;
import com.workhub.access.WorkspaceAccess;
import com.workhub.access.WorkspacePermission;
import com.workhub.common.ApiException;
import com.workhub.common.DeleteConfirmation;
import com.workhub.common.Strings;
import com.workhub.workspace.WorkspaceMemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TeamService {

	private final com.workhub.notification.NotificationService notifications;

	private final AccessService access;

	private final TeamRepository teams;

	private final TeamMemberRepository members;

	private final WorkspaceMemberRepository workspaceMembers;

	private final Clock clock;

	public TeamService(AccessService access, TeamRepository teams, TeamMemberRepository members,
			WorkspaceMemberRepository workspaceMembers, Clock clock, com.workhub.notification.NotificationService notifications) {
		this.notifications = notifications;
		this.access = access;
		this.teams = teams;
		this.members = members;
		this.workspaceMembers = workspaceMembers;
		this.clock = clock;
	}

	/** The creator becomes the team's lead. */
	public TeamResponse create(UUID userId, UUID workspaceId, TeamRequests.Save request) {
		WorkspaceAccess workspace = access.workspace(userId, workspaceId).require(WorkspacePermission.MANAGE_TEAMS);
		String name = request.name().strip();
		if (teams.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
			throw nameTaken();
		}
		Team team = teams.saveAndFlush(new Team(workspaceId, name, Strings.trimToNull(request.description()), userId));
		members.save(new TeamMember(team.getId(), userId, TeamRole.LEAD));
		return response(team, 1, new TeamAccess(team, userId, workspace.role(), TeamRole.LEAD));
	}

	@Transactional(readOnly = true)
	public List<TeamResponse> list(UUID userId, UUID workspaceId, boolean archived) {
		WorkspaceAccess workspace = access.workspace(userId, workspaceId);
		return teams.findRows(workspaceId, userId, archived)
			.stream()
			.map((row) -> new TeamResponse(row.id(), row.workspaceId(), row.name(), row.description(),
					row.archivedAt() != null, row.createdAt(), row.memberCount(), row.myRole(),
					TeamAccess.permissionsFor(workspace.role(), row.myRole())))
			.toList();
	}

	@Transactional(readOnly = true)
	public TeamResponse get(UUID userId, UUID teamId) {
		TeamAccess team = access.team(userId, teamId);
		return response(team.team(), members.countByTeamId(teamId), team);
	}

	public TeamResponse update(UUID userId, UUID teamId, TeamRequests.Save request) {
		TeamAccess team = access.team(userId, teamId).require(TeamPermission.UPDATE_TEAM).requireActive();
		String name = request.name().strip();
		if (teams.existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(team.team().getWorkspaceId(), name, teamId)) {
			throw nameTaken();
		}
		team.team().update(name, Strings.trimToNull(request.description()));
		return response(team.team(), members.countByTeamId(teamId), team);
	}

	/** Deletes the team and, through ON DELETE CASCADE, its projects with everything in them. Name must be typed. */
	public void delete(UUID userId, UUID teamId, DeleteConfirmation confirmation) {
		TeamAccess team = access.team(userId, teamId).require(TeamPermission.DELETE_TEAM);
		confirmation.require(team.team().getName());
		teams.delete(team.team());
	}

	/** Archived teams stay visible but become read-only until unarchived. */
	public TeamResponse setArchived(UUID userId, UUID teamId, boolean archived) {
		TeamAccess team = access.team(userId, teamId).require(TeamPermission.UPDATE_TEAM);
		if (archived) {
			team.team().archive(clock.instant());
		}
		else {
			team.team().unarchive();
		}
		return response(team.team(), members.countByTeamId(teamId), team);
	}

	@Transactional(readOnly = true)
	public List<TeamMemberResponse> listMembers(UUID userId, UUID teamId) {
		access.team(userId, teamId);
		return members.findMembers(teamId);
	}

	/** Adds someone who is already in the workspace; outsiders are invited instead. */
	public void addMember(UUID userId, UUID teamId, TeamRequests.AddMember request) {
		TeamAccess team = access.team(userId, teamId).require(TeamPermission.MANAGE_MEMBERS).requireActive();
		if (workspaceMembers.findByWorkspaceIdAndUserId(team.team().getWorkspaceId(), request.userId()).isEmpty()) {
			throw ApiException.badRequest("NOT_WORKSPACE_MEMBER", "User is not a member of this workspace");
		}
		if (members.existsByTeamIdAndUserId(teamId, request.userId())) {
			throw ApiException.conflict("ALREADY_MEMBER", "User is already on this team");
		}
		members.save(new TeamMember(teamId, request.userId(), request.role()));
		notifications.notify(request.userId(), com.workhub.notification.NotificationType.INVITED_TO_TEAM,
				"Bạn được thêm vào team " + team.team().getName(), null, "/teams/" + teamId, null, userId, null);
	}

	public void changeMemberRole(UUID userId, UUID teamId, UUID memberId, TeamRole role) {
		access.team(userId, teamId).require(TeamPermission.MANAGE_MEMBERS).requireActive();
		requireMember(teamId, memberId).changeRole(role);
	}

	/** Removing yourself (leaving) needs no permission. */
	public void removeMember(UUID userId, UUID teamId, UUID memberId) {
		TeamAccess team = access.team(userId, teamId);
		if (!memberId.equals(userId)) {
			team.require(TeamPermission.MANAGE_MEMBERS).requireActive();
		}
		members.delete(requireMember(teamId, memberId));
	}

	private TeamMember requireMember(UUID teamId, UUID memberId) {
		return members.findByTeamIdAndUserId(teamId, memberId)
			.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "User is not on this team"));
	}

	private static TeamResponse response(Team team, long memberCount, TeamAccess access) {
		return new TeamResponse(team.getId(), team.getWorkspaceId(), team.getName(), team.getDescription(),
				team.isArchived(), team.getCreatedAt(), memberCount, access.teamRole(), access.permissions());
	}

	private static ApiException nameTaken() {
		return ApiException.conflict("TEAM_NAME_TAKEN", "A team with this name already exists in the workspace");
	}

}
