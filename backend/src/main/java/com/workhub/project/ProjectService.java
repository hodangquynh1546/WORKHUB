package com.workhub.project;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.access.TeamAccess;
import com.workhub.access.TeamPermission;
import com.workhub.access.WorkspaceAccess;
import com.workhub.common.ApiException;
import com.workhub.common.Strings;
import com.workhub.issue.IssueRepository;
import com.workhub.team.Team;
import com.workhub.team.TeamRepository;
import com.workhub.workspace.WorkspaceMemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {

	private final AccessService access;

	private final ProjectRepository projects;

	private final ProjectMemberRepository members;

	private final TeamRepository teams;

	private final WorkspaceMemberRepository workspaceMembers;

	private final IssueRepository issues;

	public ProjectService(AccessService access, ProjectRepository projects, ProjectMemberRepository members,
			TeamRepository teams, WorkspaceMemberRepository workspaceMembers, IssueRepository issues) {
		this.access = access;
		this.projects = projects;
		this.members = members;
		this.teams = teams;
		this.workspaceMembers = workspaceMembers;
		this.issues = issues;
	}

	/** Team leads and workspace admins create projects; the creator becomes PROJECT_MANAGER. */
	public ProjectResponse create(UUID userId, UUID teamId, ProjectRequests.Create request) {
		TeamAccess team = access.team(userId, teamId).require(TeamPermission.MANAGE_PROJECTS).requireActive();
		UUID workspaceId = team.team().getWorkspaceId();
		String key = request.key().toUpperCase(Locale.ROOT);
		if (projects.existsByWorkspaceIdAndProjectKey(workspaceId, key)) {
			throw ApiException.conflict("PROJECT_KEY_TAKEN", "Another project in this workspace uses this key");
		}
		List<ProjectRequests.AddMember> initial = (request.members() != null) ? request.members() : List.of();
		Set<ProjectRole> singleRoles = new HashSet<>();
		for (ProjectRequests.AddMember member : initial) {
			requireWorkspaceMember(workspaceId, member.userId());
			if (member.role().isSingleHolder() && !singleRoles.add(member.role())) {
				throw roleTaken(member.role());
			}
		}
		Project project = projects.saveAndFlush(new Project(workspaceId, teamId, key,
				details(request.name(), request.description(), request.avatarUrl(), request.startDate(),
						request.endDate(), request.methodology()),
				userId));
		members.save(new ProjectMember(project.getId(), userId, ProjectRole.PROJECT_MANAGER));
		Set<UUID> added = new HashSet<>(Set.of(userId));
		for (ProjectRequests.AddMember member : initial) {
			if (added.add(member.userId())) {
				members.save(new ProjectMember(project.getId(), member.userId(), member.role()));
			}
		}
		ProjectAccess caller = new ProjectAccess(project, userId, team.workspaceRole(), team.teamRole(),
				ProjectRole.PROJECT_MANAGER);
		return ProjectResponse.of(project, team.team().getName(), added.size(), ProjectRole.PROJECT_MANAGER,
				caller.permissions());
	}

	/** Every project of the workspace the caller can see. */
	@Transactional(readOnly = true)
	public List<ProjectResponse> listForWorkspace(UUID userId, UUID workspaceId) {
		WorkspaceAccess workspace = access.workspace(userId, workspaceId);
		return visible(projects.findRowsInWorkspace(workspaceId, userId), workspace);
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> listForTeam(UUID userId, UUID teamId) {
		TeamAccess team = access.team(userId, teamId);
		WorkspaceAccess workspace = access.workspace(userId, team.team().getWorkspaceId());
		return visible(projects.findRowsInTeam(teamId, userId), workspace);
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(UUID userId, UUID projectId) {
		return response(access.project(userId, projectId));
	}

	public ProjectResponse update(UUID userId, UUID projectId, ProjectRequests.Update request) {
		ProjectAccess project = access.project(userId, projectId)
			.require(ProjectPermission.UPDATE_PROJECT)
			.requireActive();
		project.project()
			.update(details(request.name(), request.description(), request.avatarUrl(), request.startDate(),
					request.endDate(), request.methodology()));
		return response(project);
	}

	/** Allowed on archived projects too: changing the status is how they are unarchived. */
	public ProjectResponse changeStatus(UUID userId, UUID projectId, ProjectStatus status) {
		ProjectAccess project = access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT);
		project.project().changeStatus(status);
		return response(project);
	}

	@Transactional(readOnly = true)
	public List<ProjectMemberResponse> listMembers(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		return members.findMembers(projectId);
	}

	public void addMember(UUID userId, UUID projectId, ProjectRequests.AddMember request) {
		ProjectAccess project = manageMembers(userId, projectId);
		requireWorkspaceMember(project.project().getWorkspaceId(), request.userId());
		if (members.existsByProjectIdAndUserId(projectId, request.userId())) {
			throw ApiException.conflict("ALREADY_MEMBER", "User is already a member of this project");
		}
		requireRoleFree(projectId, request.role());
		members.save(new ProjectMember(projectId, request.userId(), request.role()));
	}

	public void changeMemberRole(UUID userId, UUID projectId, UUID memberId, ProjectRole role) {
		manageMembers(userId, projectId);
		ProjectMember member = requireMember(projectId, memberId);
		if (member.getRole() == role) {
			return;
		}
		requireRoleFree(projectId, role);
		requireNotLastManager(projectId, member);
		member.changeRole(role);
	}

	/** Removing yourself (leaving) needs no permission. */
	public void removeMember(UUID userId, UUID projectId, UUID memberId) {
		if (memberId.equals(userId)) {
			access.project(userId, projectId);
		}
		else {
			manageMembers(userId, projectId);
		}
		ProjectMember member = requireMember(projectId, memberId);
		requireNotLastManager(projectId, member);
		members.delete(member);
		// Work assigned to someone who left goes back to unassigned.
		issues.unassign(projectId, memberId);
	}

	// ---- helpers ----

	private ProjectAccess manageMembers(UUID userId, UUID projectId) {
		return access.project(userId, projectId).require(ProjectPermission.MANAGE_MEMBERS).requireActive();
	}

	private List<ProjectResponse> visible(List<ProjectRow> rows, WorkspaceAccess workspace) {
		return rows.stream()
			.map((row) -> ProjectResponse.of(row,
					ProjectAccess.permissionsFor(workspace.role(), row.myTeamRole(), row.myRole())))
			.filter((project) -> !project.permissions().isEmpty())
			.toList();
	}

	private ProjectResponse response(ProjectAccess project) {
		Team team = teams.findById(project.project().getTeamId()).orElseThrow();
		return ProjectResponse.of(project.project(), team.getName(), members.countByProjectId(project.project().getId()),
				project.projectRole(), project.permissions());
	}

	private ProjectMember requireMember(UUID projectId, UUID memberId) {
		return members.findByProjectIdAndUserId(projectId, memberId)
			.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "User is not a member of this project"));
	}

	private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
		if (!workspaceMembers.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
			throw ApiException.badRequest("NOT_WORKSPACE_MEMBER", "User is not a member of this workspace");
		}
	}

	private void requireRoleFree(UUID projectId, ProjectRole role) {
		if (role.isSingleHolder() && members.existsByProjectIdAndRole(projectId, role)) {
			throw roleTaken(role);
		}
	}

	private void requireNotLastManager(UUID projectId, ProjectMember member) {
		if (member.getRole() == ProjectRole.PROJECT_MANAGER
				&& members.countByProjectIdAndRole(projectId, ProjectRole.PROJECT_MANAGER) <= 1) {
			throw ApiException.conflict("LAST_PROJECT_MANAGER", "A project needs at least one project manager");
		}
	}

	private static ProjectDetails details(String name, String description, String avatarUrl, LocalDate startDate,
			LocalDate endDate, Methodology methodology) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw ApiException.badRequest("INVALID_DATES", "End date must not be before start date");
		}
		return new ProjectDetails(name.strip(), Strings.trimToNull(description), Strings.trimToNull(avatarUrl),
				startDate, endDate, Objects.requireNonNull(methodology));
	}

	private static ApiException roleTaken(ProjectRole role) {
		return ApiException.conflict("ROLE_TAKEN", "Only one member can be " + role);
	}

}
