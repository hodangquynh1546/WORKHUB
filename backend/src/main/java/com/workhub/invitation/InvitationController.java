package com.workhub.invitation;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	// ---- inviter side ----

	@PostMapping("/api/workspaces/{workspaceId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationRequests.Created inviteToWorkspace(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID workspaceId, @Valid @RequestBody InvitationRequests.Create request) {
		return invitationService.inviteToWorkspace(CurrentUser.id(jwt), workspaceId, request);
	}

	@GetMapping("/api/workspaces/{workspaceId}/invitations")
	public List<InvitationResponse> listForWorkspace(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID workspaceId) {
		return invitationService.listForWorkspace(CurrentUser.id(jwt), workspaceId);
	}

	@PostMapping("/api/teams/{teamId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationRequests.Created inviteToTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId,
			@Valid @RequestBody InvitationRequests.Create request) {
		return invitationService.inviteToTeam(CurrentUser.id(jwt), teamId, request);
	}

	@GetMapping("/api/teams/{teamId}/invitations")
	public List<InvitationResponse> listForTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId) {
		return invitationService.listForTeam(CurrentUser.id(jwt), teamId);
	}

	@DeleteMapping("/api/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
		invitationService.revoke(CurrentUser.id(jwt), invitationId);
	}

	// ---- invitee side ----

	/** Public (no sign-in), so the invite page can say what the link is for. */
	@GetMapping("/api/public/invitations/{token}")
	public InvitationResponse preview(@PathVariable String token) {
		return invitationService.preview(token);
	}

	@PostMapping("/api/invitations/accept")
	public InvitationRequests.Accepted accept(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody InvitationRequests.ByToken request) {
		return invitationService.acceptByToken(CurrentUser.id(jwt), request.token());
	}

	@PostMapping("/api/invitations/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void decline(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvitationRequests.ByToken request) {
		invitationService.declineByToken(CurrentUser.id(jwt), request.token());
	}

	@GetMapping("/api/me/invitations")
	public List<InvitationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
		return invitationService.listMine(CurrentUser.id(jwt));
	}

	@PostMapping("/api/me/invitations/{invitationId}/accept")
	public InvitationRequests.Accepted acceptMine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
		return invitationService.acceptById(CurrentUser.id(jwt), invitationId);
	}

	@PostMapping("/api/me/invitations/{invitationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declineMine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
		invitationService.declineById(CurrentUser.id(jwt), invitationId);
	}

}
