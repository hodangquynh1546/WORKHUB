package com.workhub.invitation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.TeamPermission;
import com.workhub.access.WorkspacePermission;
import com.workhub.auth.AuthProperties;
import com.workhub.auth.EmailSender;
import com.workhub.common.ApiException;
import com.workhub.common.SecureTokens;
import com.workhub.team.Team;
import com.workhub.team.TeamMember;
import com.workhub.team.TeamMemberRepository;
import com.workhub.team.TeamRepository;
import com.workhub.team.TeamRole;
import com.workhub.user.User;
import com.workhub.user.UserRepository;
import com.workhub.user.UserService;
import com.workhub.workspace.WorkspaceMember;
import com.workhub.workspace.WorkspaceMemberRepository;
import com.workhub.workspace.WorkspaceRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvitationService {

	private final AccessService access;

	private final InvitationRepository invitations;

	private final WorkspaceMemberRepository workspaceMembers;

	private final TeamRepository teams;

	private final TeamMemberRepository teamMembers;

	private final UserRepository users;

	private final UserService userService;

	private final EmailSender emailSender;

	private final InvitationProperties properties;

	private final AuthProperties authProperties;

	private final Clock clock;

	public InvitationService(AccessService access, InvitationRepository invitations,
			WorkspaceMemberRepository workspaceMembers, TeamRepository teams, TeamMemberRepository teamMembers,
			UserRepository users, UserService userService, EmailSender emailSender, InvitationProperties properties,
			AuthProperties authProperties, Clock clock) {
		this.access = access;
		this.invitations = invitations;
		this.workspaceMembers = workspaceMembers;
		this.teams = teams;
		this.teamMembers = teamMembers;
		this.users = users;
		this.userService = userService;
		this.emailSender = emailSender;
		this.properties = properties;
		this.authProperties = authProperties;
		this.clock = clock;
	}

	// ---- create / list / revoke (inviter side) ----

	/** Inviting an ADMIN needs MANAGE_ADMINS (owner only); inviting a MEMBER needs MANAGE_MEMBERS. */
	public InvitationRequests.Created inviteToWorkspace(UUID userId, UUID workspaceId,
			InvitationRequests.Create request) {
		WorkspaceRole role = parseWorkspaceRole(request.role());
		access.workspace(userId, workspaceId)
			.require((role == WorkspaceRole.ADMIN) ? WorkspacePermission.MANAGE_ADMINS
					: WorkspacePermission.MANAGE_MEMBERS);
		String email = emailFor(request);
		if (email != null && users.findByEmail(email)
			.filter((invitee) -> workspaceMembers.existsByWorkspaceIdAndUserId(workspaceId, invitee.getId()))
			.isPresent()) {
			throw alreadyMember();
		}
		return create(workspaceId, null, request.kind(), email, role.name(), userId);
	}

	/** Team leads (and workspace admins) can invite; an invitee outside the workspace joins it as MEMBER. */
	public InvitationRequests.Created inviteToTeam(UUID userId, UUID teamId, InvitationRequests.Create request) {
		TeamRole role = parseTeamRole(request.role());
		Team team = access.team(userId, teamId).require(TeamPermission.MANAGE_MEMBERS).requireActive().team();
		String email = emailFor(request);
		if (email != null && users.findByEmail(email)
			.filter((invitee) -> teamMembers.existsByTeamIdAndUserId(teamId, invitee.getId()))
			.isPresent()) {
			throw alreadyMember();
		}
		return create(team.getWorkspaceId(), teamId, request.kind(), email, role.name(), userId);
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listForWorkspace(UUID userId, UUID workspaceId) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.MANAGE_MEMBERS);
		return invitations.findPendingInWorkspace(workspaceId, clock.instant());
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listForTeam(UUID userId, UUID teamId) {
		access.team(userId, teamId).require(TeamPermission.MANAGE_MEMBERS);
		return invitations.findPendingInTeam(teamId, clock.instant());
	}

	public void revoke(UUID userId, UUID invitationId) {
		Invitation invitation = invitations.findById(invitationId).orElseThrow(InvitationService::notFound);
		requireCanManage(userId, invitation);
		if (invitation.getStatus() == InvitationStatus.PENDING) {
			invitation.revoke();
		}
	}

	// ---- preview / accept / decline (invitee side) ----

	/** Public: lets the invite page show what the link is for before the user signs in. */
	@Transactional(readOnly = true)
	public InvitationResponse preview(String token) {
		Invitation invitation = byToken(token);
		return invitations.findView(invitation.getId()).withStatus(invitation.statusAt(clock.instant()));
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listMine(UUID userId) {
		return invitations.findPendingForEmail(userService.require(userId).getEmail(), clock.instant());
	}

	public InvitationRequests.Accepted acceptByToken(UUID userId, String token) {
		return accept(userId, byToken(token));
	}

	/** Only for email invitations, which the invitee sees in their own list. */
	public InvitationRequests.Accepted acceptById(UUID userId, UUID invitationId) {
		return accept(userId, emailInvitation(invitationId));
	}

	public void declineByToken(UUID userId, String token) {
		decline(userId, byToken(token));
	}

	public void declineById(UUID userId, UUID invitationId) {
		decline(userId, emailInvitation(invitationId));
	}

	private InvitationRequests.Accepted accept(UUID userId, Invitation invitation) {
		Instant now = clock.instant();
		requireUsable(invitation, now);
		User user = userService.require(userId);
		requireAddressee(invitation, user);
		Team team = (invitation.getTeamId() != null)
				? teams.findById(invitation.getTeamId()).orElseThrow(InvitationService::notFound) : null;
		if (team != null && team.isArchived()) {
			throw ApiException.conflict("TEAM_ARCHIVED", "Team is archived");
		}
		// Existing memberships are kept as they are; an invitation never changes someone's role.
		if (!workspaceMembers.existsByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId)) {
			WorkspaceRole role = (team == null) ? WorkspaceRole.valueOf(invitation.getRole()) : WorkspaceRole.MEMBER;
			workspaceMembers.save(new WorkspaceMember(invitation.getWorkspaceId(), userId, role));
		}
		if (team != null && !teamMembers.existsByTeamIdAndUserId(team.getId(), userId)) {
			teamMembers.save(new TeamMember(team.getId(), userId, TeamRole.valueOf(invitation.getRole())));
		}
		if (invitation.getKind() == InvitationKind.EMAIL) {
			invitation.respond(InvitationStatus.ACCEPTED, userId, now);
		}
		return new InvitationRequests.Accepted(invitation.getWorkspaceId(), invitation.getTeamId());
	}

	private void decline(UUID userId, Invitation invitation) {
		if (invitation.getKind() != InvitationKind.EMAIL) {
			throw ApiException.badRequest("INVITATION_INVALID", "Only email invitations can be declined");
		}
		Instant now = clock.instant();
		requireUsable(invitation, now);
		requireAddressee(invitation, userService.require(userId));
		invitation.respond(InvitationStatus.DECLINED, userId, now);
	}

	// ---- helpers ----

	private InvitationRequests.Created create(UUID workspaceId, UUID teamId, InvitationKind kind, String email,
			String role, UUID inviterId) {
		if (kind == InvitationKind.EMAIL) {
			// Re-inviting the same address replaces the previous link.
			invitations
				.findAllByWorkspaceIdAndTeamIdAndKindAndEmailAndStatus(workspaceId, teamId, kind, email,
						InvitationStatus.PENDING)
				.forEach(Invitation::revoke);
		}
		String token = SecureTokens.newToken();
		Invitation invitation = invitations.save(new Invitation(workspaceId, teamId, kind, email, role,
				SecureTokens.hash(token), inviterId, clock.instant().plus(properties.ttl())));
		invitations.flush();
		InvitationResponse view = invitations.findView(invitation.getId());
		String url = authProperties.frontendBaseUrl() + "/invitations/" + token;
		if (kind == InvitationKind.EMAIL) {
			sendEmail(view, url);
		}
		return new InvitationRequests.Created(view, url);
	}

	private void sendEmail(InvitationResponse view, String url) {
		String target = (view.teamName() != null)
				? "team " + view.teamName() + " (workspace " + view.workspaceName() + ")"
				: "workspace " + view.workspaceName();
		emailSender.send(view.email(), view.invitedByName() + " mời bạn tham gia " + target + " trên WORKHUB",
				"Xin chào,\n\n" + view.invitedByName() + " đã mời bạn tham gia " + target + ".\n\n"
						+ "Mở liên kết sau để chấp nhận hoặc từ chối:\n" + url + "\n\nLời mời có hiệu lực trong "
						+ properties.ttl().toDays() + " ngày. Nếu chưa có tài khoản, hãy đăng ký bằng chính email này.");
	}

	private void requireCanManage(UUID userId, Invitation invitation) {
		if (invitation.getTeamId() != null) {
			access.team(userId, invitation.getTeamId()).require(TeamPermission.MANAGE_MEMBERS);
		}
		else {
			access.workspace(userId, invitation.getWorkspaceId()).require(WorkspacePermission.MANAGE_MEMBERS);
		}
	}

	private Invitation byToken(String token) {
		return invitations.findByTokenHash(SecureTokens.hash(token)).orElseThrow(InvitationService::notFound);
	}

	private Invitation emailInvitation(UUID invitationId) {
		return invitations.findById(invitationId)
			.filter((invitation) -> invitation.getKind() == InvitationKind.EMAIL)
			.orElseThrow(InvitationService::notFound);
	}

	private static void requireUsable(Invitation invitation, Instant now) {
		if (invitation.statusAt(now) != InvitationStatus.PENDING) {
			throw ApiException.badRequest("INVITATION_INVALID", "Invitation is " + invitation.statusAt(now));
		}
	}

	/** Email invitations belong to one address; link invitations to whoever holds the link. */
	private static void requireAddressee(Invitation invitation, User user) {
		if (invitation.getKind() == InvitationKind.EMAIL && !invitation.getEmail().equals(user.getEmail())) {
			throw ApiException.forbidden("INVITATION_EMAIL_MISMATCH", "This invitation was sent to another email");
		}
	}

	private static String emailFor(InvitationRequests.Create request) {
		if (request.kind() == InvitationKind.LINK) {
			return null;
		}
		if (request.email() == null || request.email().isBlank()) {
			throw ApiException.badRequest("EMAIL_REQUIRED", "Email invitations need an email address");
		}
		return User.normalizeEmail(request.email());
	}

	private static WorkspaceRole parseWorkspaceRole(String role) {
		return switch (role) {
			case "ADMIN" -> WorkspaceRole.ADMIN;
			case "MEMBER" -> WorkspaceRole.MEMBER;
			default -> throw invalidRole();
		};
	}

	private static TeamRole parseTeamRole(String role) {
		return switch (role) {
			case "LEAD" -> TeamRole.LEAD;
			case "MEMBER" -> TeamRole.MEMBER;
			default -> throw invalidRole();
		};
	}

	private static ApiException invalidRole() {
		return ApiException.badRequest("INVALID_ROLE", "Role is not allowed for this invitation");
	}

	private static ApiException alreadyMember() {
		return ApiException.conflict("ALREADY_MEMBER", "This person is already a member");
	}

	private static ApiException notFound() {
		return ApiException.notFound("INVITATION_NOT_FOUND", "Invitation not found");
	}

}
