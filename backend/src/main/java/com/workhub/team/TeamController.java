package com.workhub.team;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamController {

	private final TeamService teamService;

	public TeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@PostMapping("/api/workspaces/{workspaceId}/teams")
	@ResponseStatus(HttpStatus.CREATED)
	public TeamResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@Valid @RequestBody TeamRequests.Save request) {
		return teamService.create(CurrentUser.id(jwt), workspaceId, request);
	}

	@GetMapping("/api/workspaces/{workspaceId}/teams")
	public List<TeamResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@RequestParam(defaultValue = "false") boolean archived) {
		return teamService.list(CurrentUser.id(jwt), workspaceId, archived);
	}

	@GetMapping("/api/teams/{teamId}")
	public TeamResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return teamService.get(CurrentUser.id(jwt), teamId);
	}

	@PutMapping("/api/teams/{teamId}")
	public TeamResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId,
			@Valid @RequestBody TeamRequests.Save request) {
		return teamService.update(CurrentUser.id(jwt), teamId, request);
	}

	@DeleteMapping("/api/teams/{teamId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId,
			@Valid @RequestBody DeleteConfirmation confirmation) {
		teamService.delete(CurrentUser.id(jwt), teamId, confirmation);
	}

	@PostMapping("/api/teams/{teamId}/archive")
	public TeamResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return teamService.setArchived(CurrentUser.id(jwt), teamId, true);
	}

	@PostMapping("/api/teams/{teamId}/unarchive")
	public TeamResponse unarchive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return teamService.setArchived(CurrentUser.id(jwt), teamId, false);
	}

	@GetMapping("/api/teams/{teamId}/members")
	public List<TeamMemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return teamService.listMembers(CurrentUser.id(jwt), teamId);
	}

	@PostMapping("/api/teams/{teamId}/members")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId,
			@Valid @RequestBody TeamRequests.AddMember request) {
		teamService.addMember(CurrentUser.id(jwt), teamId, request);
	}

	@PutMapping("/api/teams/{teamId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId, @PathVariable UUID userId,
			@Valid @RequestBody TeamRequests.ChangeRole request) {
		teamService.changeMemberRole(CurrentUser.id(jwt), teamId, userId, request.role());
	}

	@DeleteMapping("/api/teams/{teamId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId, @PathVariable UUID userId) {
		teamService.removeMember(CurrentUser.id(jwt), teamId, userId);
	}

}
