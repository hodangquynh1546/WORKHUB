package com.workhub.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping("/api/teams/{teamId}/projects")
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId,
			@Valid @RequestBody ProjectRequests.Create request) {
		return projectService.create(CurrentUser.id(jwt), teamId, request);
	}

	@GetMapping("/api/teams/{teamId}/projects")
	public List<ProjectResponse> listForTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return projectService.listForTeam(CurrentUser.id(jwt), teamId);
	}

	@GetMapping("/api/workspaces/{workspaceId}/projects")
	public List<ProjectResponse> listForWorkspace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
		return projectService.listForWorkspace(CurrentUser.id(jwt), workspaceId);
	}

	@GetMapping("/api/projects/{projectId}")
	public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return projectService.get(CurrentUser.id(jwt), projectId);
	}

	@PutMapping("/api/projects/{projectId}")
	public ProjectResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody ProjectRequests.Update request) {
		return projectService.update(CurrentUser.id(jwt), projectId, request);
	}

	@PutMapping("/api/projects/{projectId}/status")
	public ProjectResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody ProjectRequests.ChangeStatus request) {
		return projectService.changeStatus(CurrentUser.id(jwt), projectId, request.status());
	}

	@GetMapping("/api/projects/{projectId}/members")
	public List<ProjectMemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return projectService.listMembers(CurrentUser.id(jwt), projectId);
	}

	@PostMapping("/api/projects/{projectId}/members")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody ProjectRequests.AddMember request) {
		projectService.addMember(CurrentUser.id(jwt), projectId, request);
	}

	@PutMapping("/api/projects/{projectId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID userId,
			@Valid @RequestBody ProjectRequests.ChangeRole request) {
		projectService.changeMemberRole(CurrentUser.id(jwt), projectId, userId, request.role());
	}

	@DeleteMapping("/api/projects/{projectId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@PathVariable UUID userId) {
		projectService.removeMember(CurrentUser.id(jwt), projectId, userId);
	}

}
