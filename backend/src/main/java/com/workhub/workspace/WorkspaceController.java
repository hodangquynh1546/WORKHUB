package com.workhub.workspace;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.common.DeleteConfirmation;
import com.workhub.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService workspaceService;

	public WorkspaceController(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WorkspaceResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkspaceRequest request) {
		return workspaceService.create(CurrentUser.id(jwt), request);
	}

	@GetMapping
	public List<WorkspaceResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return workspaceService.listForUser(CurrentUser.id(jwt));
	}

	@GetMapping("/{workspaceId}")
	public WorkspaceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
		return workspaceService.get(CurrentUser.id(jwt), workspaceId);
	}

	@PutMapping("/{workspaceId}")
	public WorkspaceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@Valid @RequestBody WorkspaceRequest request) {
		return workspaceService.update(CurrentUser.id(jwt), workspaceId, request);
	}

	@DeleteMapping("/{workspaceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@Valid @RequestBody DeleteConfirmation confirmation) {
		workspaceService.delete(CurrentUser.id(jwt), workspaceId, confirmation);
	}

	@PostMapping("/{workspaceId}/transfer-ownership")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void transferOwnership(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@Valid @RequestBody TransferOwnershipRequest request) {
		workspaceService.transferOwnership(CurrentUser.id(jwt), workspaceId, request);
	}

	@GetMapping("/{workspaceId}/members")
	public List<WorkspaceMemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
		return workspaceService.listMembers(CurrentUser.id(jwt), workspaceId);
	}

	@PutMapping("/{workspaceId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changeMemberRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@PathVariable UUID userId, @Valid @RequestBody ChangeWorkspaceRoleRequest request) {
		workspaceService.changeMemberRole(CurrentUser.id(jwt), workspaceId, userId, request.role());
	}

	/** Removing yourself means leaving the workspace. */
	@DeleteMapping("/{workspaceId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@PathVariable UUID userId) {
		workspaceService.removeMember(CurrentUser.id(jwt), workspaceId, userId);
	}

}
