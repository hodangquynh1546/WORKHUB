package com.workhub.workspace;

import java.util.List;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.common.Strings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every operation takes the caller's id and checks membership here, in the backend.
 * Non-members get 404 rather than 403, so workspace ids cannot be probed.
 */
@Service
@Transactional
public class WorkspaceService {

	private final WorkspaceRepository workspaces;

	private final WorkspaceMemberRepository members;

	public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMemberRepository members) {
		this.workspaces = workspaces;
		this.members = members;
	}

	/** The creator becomes the workspace owner. */
	public WorkspaceResponse create(UUID userId, WorkspaceRequest request) {
		Workspace workspace = workspaces.save(new Workspace(request.name().strip(),
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
		WorkspaceRole role = requireRole(userId, workspaceId);
		return WorkspaceResponse.of(load(workspaceId), role);
	}

	public WorkspaceResponse update(UUID userId, UUID workspaceId, WorkspaceRequest request) {
		WorkspaceRole role = requireRole(userId, workspaceId);
		if (!role.canManage()) {
			throw ApiException.forbidden("FORBIDDEN", "Only workspace owners and admins can change settings");
		}
		Workspace workspace = load(workspaceId);
		workspace.update(request.name().strip(), Strings.trimToNull(request.description()),
				Strings.trimToNull(request.logoUrl()));
		return WorkspaceResponse.of(workspace, role);
	}

	@Transactional(readOnly = true)
	public List<WorkspaceMemberResponse> listMembers(UUID userId, UUID workspaceId) {
		requireRole(userId, workspaceId);
		return members.findMembers(workspaceId);
	}

	/** The caller's role in the workspace; 404 when they are not a member. */
	public WorkspaceRole requireRole(UUID userId, UUID workspaceId) {
		return members.findByWorkspaceIdAndUserId(workspaceId, userId)
			.map(WorkspaceMember::getRole)
			.orElseThrow(WorkspaceService::notFound);
	}

	private Workspace load(UUID workspaceId) {
		return workspaces.findById(workspaceId).orElseThrow(WorkspaceService::notFound);
	}

	private static ApiException notFound() {
		return ApiException.notFound("WORKSPACE_NOT_FOUND", "Workspace not found");
	}

}
