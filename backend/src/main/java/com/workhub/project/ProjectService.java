package com.workhub.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
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
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintStatus;
import com.workhub.team.Team;
import com.workhub.team.TeamRepository;
import com.workhub.workspace.WorkspaceMemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {

	private final com.workhub.notification.NotificationService notifications;

	private final AccessService access;

	private final ProjectRepository projects;

	private final ProjectMemberRepository members;

	private final TeamRepository teams;

	private final WorkspaceMemberRepository workspaceMembers;

	private final IssueRepository issues;

	private final SprintRepository sprints;

	private final ProjectAvatarRepository avatars;

	private final java.time.Clock clock;

	public ProjectService(AccessService access, ProjectRepository projects, ProjectMemberRepository members,
			TeamRepository teams, WorkspaceMemberRepository workspaceMembers, IssueRepository issues,
			SprintRepository sprints, com.workhub.notification.NotificationService notifications,
			ProjectAvatarRepository avatars, java.time.Clock clock) {
		this.notifications = notifications;
		this.access = access;
		this.projects = projects;
		this.members = members;
		this.teams = teams;
		this.workspaceMembers = workspaceMembers;
		this.issues = issues;
		this.sprints = sprints;
		this.avatars = avatars;
		this.clock = clock;
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
		Set<ProjectRole> singleRoles = new HashSet<>(Set.of(ProjectRole.PROJECT_MANAGER)); // the creator holds it
		for (ProjectRequests.AddMember member : initial) {
			requireWorkspaceMember(workspaceId, member.userId());
			if (member.role().isSingleHolder() && !singleRoles.add(member.role())) {
				throw roleTaken(member.role());
			}
		}
		Project project = projects.saveAndFlush(new Project(workspaceId, teamId, key,
				details(request.name(), request.description(), null, request.startDate(),
						request.endDate(), request.methodology()),
				userId));
		members.save(new ProjectMember(project.getId(), userId, ProjectRole.PROJECT_MANAGER));
		Set<UUID> added = new HashSet<>(Set.of(userId));
		for (ProjectRequests.AddMember member : initial) {
			if (added.add(member.userId())) {
				members.save(new ProjectMember(project.getId(), member.userId(), member.role()));
			}
		}
		Set<ProjectRole> mine = Set.of(ProjectRole.PROJECT_MANAGER);
		ProjectAccess caller = new ProjectAccess(project, userId, team.workspaceRole(), team.teamRole(), mine);
		return ProjectResponse.of(project, userId, team.team().getName(), added.size(), mine,
				caller.permissions());
	}

	/** Every project the caller can reach, across all their workspaces. */
	@Transactional(readOnly = true)
	public List<ProjectResponse> listMine(UUID userId) {
		return workspaceMembers.findWorkspaceIds(userId).stream().flatMap((id) -> listForWorkspace(userId, id).stream()).toList();
	}

	/** Every project of the workspace the caller can see. */
	@Transactional(readOnly = true)
	public List<ProjectResponse> listForWorkspace(UUID userId, UUID workspaceId) {
		WorkspaceAccess workspace = access.workspace(userId, workspaceId);
		return visible(userId, projects.findRowsInWorkspace(workspaceId, userId), workspace);
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> listForTeam(UUID userId, UUID teamId) {
		TeamAccess team = access.team(userId, teamId);
		WorkspaceAccess workspace = access.workspace(userId, team.team().getWorkspaceId());
		return visible(userId, projects.findRowsInTeam(teamId, userId), workspace);
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(UUID userId, UUID projectId) {
		return response(access.project(userId, projectId));
	}

	public ProjectResponse update(UUID userId, UUID projectId, ProjectRequests.Update request) {
		ProjectAccess project = access.project(userId, projectId)
			.require(ProjectPermission.UPDATE_PROJECT)
			.requireActive();
		if (request.methodology() == Methodology.KANBAN && project.project().getMethodology() == Methodology.SCRUM
				&& sprints.existsByProjectIdAndStatusIn(projectId, List.of(SprintStatus.FUTURE, SprintStatus.ACTIVE))) {
			throw ApiException.conflict("OPEN_SPRINTS", "Complete or cancel the open sprints before switching to Kanban");
		}
		project.project()
			.update(details(request.name(), request.description(), null, request.startDate(),
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
		java.util.Map<UUID, List<MemberRoleRow>> byPerson = new java.util.LinkedHashMap<>();
		for (MemberRoleRow row : members.findMemberRoles(projectId)) {
			byPerson.computeIfAbsent(row.userId(), (id) -> new java.util.ArrayList<>()).add(row);
		}
		return byPerson.values().stream().map((rows) -> {
			MemberRoleRow first = rows.get(0);
			List<ProjectRole> roles = rows.stream().map(MemberRoleRow::role).sorted().toList();
			Instant joined = rows.stream().map(MemberRoleRow::joinedAt).min(Comparator.naturalOrder()).orElse(first.joinedAt());
			return new ProjectMemberResponse(first.userId(), first.email(), first.fullName(), first.avatarUrl(), roles.get(0), roles, joined);
		}).sorted(Comparator.comparing((ProjectMemberResponse m) -> m.role()).thenComparing((m) -> m.fullName().toLowerCase(Locale.ROOT))).toList();
	}

	public void addMember(UUID userId, UUID projectId, ProjectRequests.AddMember request) {
		ProjectAccess project = manageMembers(userId, projectId);
		requireWorkspaceMember(project.project().getWorkspaceId(), request.userId());
		if (members.existsByProjectIdAndUserId(projectId, request.userId())) {
			throw ApiException.conflict("ALREADY_MEMBER", "User is already a member of this project");
		}
		applyRoles(projectId, request.userId(), Set.of(request.role()));
		notifications.notify(request.userId(), com.workhub.notification.NotificationType.INVITED_TO_PROJECT,
				"Bạn được thêm vào dự án " + project.project().getName(), null, "/projects/" + projectId, projectId, userId, null);
	}

	/** Replaces the member's whole set of roles. */
	public void changeMemberRoles(UUID userId, UUID projectId, UUID memberId, Set<ProjectRole> roles) {
		manageMembers(userId, projectId);
		if (members.findAllByProjectIdAndUserId(projectId, memberId).isEmpty()) {
			throw ApiException.notFound("MEMBER_NOT_FOUND", "User is not a member of this project");
		}
		applyRoles(projectId, memberId, roles);
	}

	/** Removing yourself (leaving) needs no permission. */
	public void removeMember(UUID userId, UUID projectId, UUID memberId) {
		if (memberId.equals(userId)) {
			access.project(userId, projectId);
		}
		else {
			manageMembers(userId, projectId);
		}
		List<ProjectMember> held = requireMember(projectId, memberId);
		if (held.stream().anyMatch((m) -> m.getRole() == ProjectRole.PROJECT_MANAGER)) {
			throw ApiException.conflict("LAST_PROJECT_MANAGER", "A project needs a project manager: hand the role to someone else first");
		}
		members.deleteAll(held);
		// Work assigned to someone who left goes back to unassigned.
		issues.unassign(projectId, memberId);
	}

	private static final int MAX_AVATAR_BYTES = 512 * 1024;

	/** Stores the picture and points {@code avatarUrl} at it; the version in the URL busts browser caches. */
	public ProjectResponse uploadAvatar(UUID userId, UUID projectId, String contentType, byte[] data) {
		ProjectAccess project = access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT).requireActive();
		if (data.length == 0 || data.length > MAX_AVATAR_BYTES) {
			throw ApiException.badRequest("AVATAR_TOO_LARGE", "Picture must be between 1 byte and 512 KB");
		}
		if (contentType == null || !com.workhub.common.ImageTypes.matches(contentType, data)) {
			throw ApiException.badRequest("AVATAR_INVALID_TYPE", "Picture must be a JPEG, PNG or WebP image");
		}
		Instant now = Instant.now(clock);
		avatars.save(new ProjectAvatar(projectId, contentType, data, now));
		project.project().changeAvatarUrl("/api/public/projects/" + projectId + "/avatar?v=" + now.toEpochMilli());
		return response(project);
	}

	public ProjectResponse removeAvatar(UUID userId, UUID projectId) {
		ProjectAccess project = access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT).requireActive();
		avatars.deleteById(projectId);
		project.project().changeAvatarUrl(null);
		return response(project);
	}

	@Transactional(readOnly = true)
	public ProjectAvatar avatar(UUID projectId) {
		return avatars.findById(projectId).orElseThrow(() -> ApiException.notFound("AVATAR_NOT_FOUND", "No picture"));
	}

	// ---- helpers ----

	private ProjectAccess manageMembers(UUID userId, UUID projectId) {
		return access.project(userId, projectId).require(ProjectPermission.MANAGE_MEMBERS).requireActive();
	}

	private List<ProjectResponse> visible(UUID userId, List<ProjectRow> rows, WorkspaceAccess workspace) {
		java.util.Map<UUID, Set<ProjectRole>> mine = new java.util.HashMap<>();
		if (!rows.isEmpty()) {
			for (Object[] pair : members.findRolesOfUser(userId, rows.stream().map(ProjectRow::id).toList())) {
				mine.computeIfAbsent((UUID) pair[0], (id) -> java.util.EnumSet.noneOf(ProjectRole.class)).add((ProjectRole) pair[1]);
			}
		}
		return rows.stream()
			.map((row) -> {
				Set<ProjectRole> roles = mine.getOrDefault(row.id(), Set.of());
				return ProjectResponse.of(row, userId, roles, ProjectAccess.permissionsFor(workspace.role(), row.myTeamRole(), roles));
			})
			.filter((project) -> !project.permissions().isEmpty())
			.toList();
	}

	private ProjectResponse response(ProjectAccess project) {
		Team team = teams.findById(project.project().getTeamId()).orElseThrow();
		return ProjectResponse.of(project.project(), project.userId(), team.getName(), members.countByProjectId(project.project().getId()),
				project.projectRoles(), project.permissions());
	}

	private List<ProjectMember> requireMember(UUID projectId, UUID memberId) {
		List<ProjectMember> held = members.findAllByProjectIdAndUserId(projectId, memberId);
		if (held.isEmpty()) {
			throw ApiException.notFound("MEMBER_NOT_FOUND", "User is not a member of this project");
		}
		return held;
	}

	private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
		if (!workspaceMembers.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
			throw ApiException.badRequest("NOT_WORKSPACE_MEMBER", "User is not a member of this workspace");
		}
	}

	/**
	 * Gives the member exactly these roles. PROJECT_MANAGER and SCRUM_MASTER have one holder, so taking one over moves
	 * it from the previous holder (who keeps their other roles, or becomes a DEVELOPER if that was their only one).
	 * The project manager cannot simply drop the role: it has to be handed to someone else.
	 */
	private void applyRoles(UUID projectId, UUID memberId, Set<ProjectRole> wanted) {
		List<ProjectMember> held = members.findAllByProjectIdAndUserId(projectId, memberId);
		boolean wasManager = held.stream().anyMatch((m) -> m.getRole() == ProjectRole.PROJECT_MANAGER);
		if (wasManager && !wanted.contains(ProjectRole.PROJECT_MANAGER)) {
			throw ApiException.conflict("LAST_PROJECT_MANAGER", "A project needs a project manager: hand the role to someone else first");
		}
		for (ProjectRole role : wanted) {
			if (!role.isSingleHolder()) {
				continue;
			}
			for (ProjectMember other : members.findAllByProjectIdAndRole(projectId, role)) {
				if (other.getUserId().equals(memberId)) {
					continue;
				}
				members.delete(other);
				members.flush();
				if (members.findAllByProjectIdAndUserId(projectId, other.getUserId()).isEmpty()) {
					members.save(new ProjectMember(projectId, other.getUserId(), ProjectRole.DEVELOPER));
				}
			}
		}
		for (ProjectMember row : held) {
			if (!wanted.contains(row.getRole())) {
				members.delete(row);
			}
		}
		Set<ProjectRole> had = new HashSet<>();
		held.forEach((m) -> had.add(m.getRole()));
		for (ProjectRole role : wanted) {
			if (!had.contains(role)) {
				members.save(new ProjectMember(projectId, memberId, role));
			}
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
