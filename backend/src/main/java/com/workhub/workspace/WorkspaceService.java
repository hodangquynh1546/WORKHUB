package com.workhub.workspace;

import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.WorkspaceAccess;
import com.workhub.access.WorkspacePermission;
import com.workhub.common.ApiException;
import com.workhub.common.DeleteConfirmation;
import com.workhub.common.Strings;
import com.workhub.issue.IssueRepository;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.team.TeamMemberRepository;
import com.workhub.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission checks go through {@link AccessService}; non-members get 404 so workspace ids cannot be probed. */
@Service
@Transactional
public class WorkspaceService {

	private final AccessService access;

	private final WorkspaceRepository workspaces;

	private final WorkspaceMemberRepository members;

	private final TeamMemberRepository teamMembers;

	private final ProjectMemberRepository projectMembers;

	private final IssueRepository issues;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final com.workhub.common.CascadeCleaner cascadeCleaner;

	public WorkspaceService(AccessService access, WorkspaceRepository workspaces, WorkspaceMemberRepository members,
			TeamMemberRepository teamMembers, ProjectMemberRepository projectMembers, IssueRepository issues,
			UserRepository users, PasswordEncoder passwordEncoder, com.workhub.common.CascadeCleaner cascadeCleaner) {
		this.access = access;
		this.workspaces = workspaces;
		this.members = members;
		this.teamMembers = teamMembers;
		this.projectMembers = projectMembers;
		this.issues = issues;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.cascadeCleaner = cascadeCleaner;
	}

	/** The creator becomes the workspace owner. */
	public WorkspaceResponse create(UUID userId, WorkspaceRequest request) {
		Workspace workspace = workspaces.saveAndFlush(new Workspace(request.name().strip(),
				Strings.trimToNull(request.description()), Strings.trimToNull(request.logoUrl()), userId));
		members.save(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER));
		return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
	}

	@Transactional(readOnly = true)
	public List<WorkspaceResponse> listForUser(UUID userId) {
		return workspaces.findAllForMember(userId);
	}

	@Transactional(readOnly = true)
	public WorkspaceResponse get(UUID userId, UUID workspaceId) {
		WorkspaceAccess caller = notGuest(access.workspace(userId, workspaceId));
		return WorkspaceResponse.of(load(workspaceId), caller.role());
	}

	public WorkspaceResponse update(UUID userId, UUID workspaceId, WorkspaceRequest request) {
		WorkspaceAccess caller = access.workspace(userId, workspaceId).require(WorkspacePermission.UPDATE_SETTINGS);
		Workspace workspace = load(workspaceId);
		workspace.update(request.name().strip(), Strings.trimToNull(request.description()),
				Strings.trimToNull(request.logoUrl()));
		return WorkspaceResponse.of(workspace, caller.role());
	}

	/**
	 * Deletes the workspace for good: teams, projects, issues, documents, chats and invitations go with it through the
	 * database's ON DELETE CASCADE. Only the owner may, and must type the workspace's name.
	 */
	public void delete(UUID userId, UUID workspaceId, DeleteConfirmation confirmation) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.DELETE_WORKSPACE);
		Workspace workspace = load(workspaceId);
		confirmation.require(workspace.getName());
		cascadeCleaner.beforeDeletingWorkspace(workspaceId);
		workspaces.delete(workspace);
	}

	/** The owner hands the workspace to another member and becomes an ADMIN; there is still exactly one OWNER. */
	public void transferOwnership(UUID userId, UUID workspaceId, TransferOwnershipRequest request) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.TRANSFER_OWNERSHIP);
		if (!passwordEncoder.matches(request.password(), users.findById(userId).orElseThrow().getPasswordHash())) {
			throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
		}
		if (request.userId().equals(userId)) {
			throw ApiException.badRequest("ALREADY_OWNER", "You already own this workspace");
		}
		WorkspaceMember next = requireMember(workspaceId, request.userId());
		WorkspaceMember current = requireMember(workspaceId, userId);
		current.changeRole(WorkspaceRole.ADMIN);
		next.changeRole(WorkspaceRole.OWNER);
	}

	@Transactional(readOnly = true)
	public List<WorkspaceMemberResponse> listMembers(UUID userId, UUID workspaceId) {
		notGuest(access.workspace(userId, workspaceId));
		return members.findMembers(workspaceId);
	}

	/** Only the owner promotes or demotes admins. The owner's own role cannot change here. */
	public void changeMemberRole(UUID userId, UUID workspaceId, UUID memberId, WorkspaceRole role) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.MANAGE_ADMINS);
		if (role == WorkspaceRole.OWNER) {
			throw ApiException.badRequest("INVALID_ROLE", "Ownership cannot be assigned this way");
		}
		WorkspaceMember member = requireMember(workspaceId, memberId);
		if (member.getRole() == WorkspaceRole.OWNER) {
			throw ApiException.forbidden("OWNER_PROTECTED", "The owner's role cannot be changed");
		}
		member.changeRole(role);
	}

	/**
	 * Removes a member (or lets the caller leave) and drops them from every team and project of the workspace.
	 * This may leave a project without a project manager; its team lead and workspace admins still manage it.
	 * Admins can remove members; only the owner can remove admins; nobody can remove the owner.
	 */
	public void removeMember(UUID userId, UUID workspaceId, UUID memberId) {
		WorkspaceAccess caller = access.workspace(userId, workspaceId);
		WorkspaceMember member = requireMember(workspaceId, memberId);
		boolean leaving = memberId.equals(userId);
		if (member.getRole() == WorkspaceRole.OWNER) {
			throw leaving ? ApiException.conflict("OWNER_CANNOT_LEAVE", "The owner cannot leave the workspace")
					: ApiException.forbidden("OWNER_PROTECTED", "The owner cannot be removed");
		}
		if (!leaving) {
			caller.require((member.getRole() == WorkspaceRole.ADMIN) ? WorkspacePermission.MANAGE_ADMINS
					: WorkspacePermission.MANAGE_MEMBERS);
		}
		members.delete(member);
		teamMembers.deleteAllInWorkspace(workspaceId, memberId);
		projectMembers.deleteAllInWorkspace(workspaceId, memberId);
		issues.unassignAllInWorkspace(workspaceId, memberId);
	}

	private WorkspaceMember requireMember(UUID workspaceId, UUID memberId) {
		return members.findByWorkspaceIdAndUserId(workspaceId, memberId)
			.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "User is not a member of this workspace"));
	}

	private Workspace load(UUID workspaceId) {
		return workspaces.findById(workspaceId)
			.orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "Workspace not found"));
	}

	/** Guests (invited to a team or project only) do not see the workspace itself or who else is in it. */
	private static WorkspaceAccess notGuest(WorkspaceAccess access) {
		if (access.guest()) {
			throw ApiException.notFound("WORKSPACE_NOT_FOUND", "Workspace not found");
		}
		return access;
	}

}
