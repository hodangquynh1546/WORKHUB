package com.workhub.invitation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.workhub.realtime.RealtimeHub;
import com.workhub.user.UserRepository;
import com.workhub.workspace.WorkspaceMemberRepository;
import com.workhub.workspace.WorkspaceMemberResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells open sessions that invitations changed (sent, accepted, declined, revoked) so lists reload by themselves.
 * The frame carries no data: the invitee (looked up by email) and the members of the workspace reload what they show.
 * It runs after the commit and never fails the caller.
 */
@Component
class InvitationRealtime {

	private static final Logger log = LoggerFactory.getLogger(InvitationRealtime.class);

	/** {@code email} is null for link invitations. */
	record Changed(UUID workspaceId, UUID teamId, UUID projectId, String email, UUID actorId) {
	}

	record Frame(String type, UUID workspaceId, UUID teamId, UUID projectId) {
	}

	private final RealtimeHub hub;

	private final UserRepository users;

	private final WorkspaceMemberRepository workspaceMembers;

	InvitationRealtime(RealtimeHub hub, UserRepository users, WorkspaceMemberRepository workspaceMembers) {
		this.hub = hub;
		this.users = users;
		this.workspaceMembers = workspaceMembers;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void on(Changed changed) {
		try {
			Set<UUID> recipients = new HashSet<>();
			if (changed.email() != null) {
				users.findByEmail(changed.email()).ifPresent((user) -> recipients.add(user.getId()));
			}
			for (WorkspaceMemberResponse member : workspaceMembers.findMembers(changed.workspaceId())) {
				recipients.add(member.userId());
			}
			if (changed.actorId() != null) {
				recipients.add(changed.actorId());
			}
			hub.sendToUsers(recipients, new Frame("invitations.changed", changed.workspaceId(), changed.teamId(), changed.projectId()));
		}
		catch (RuntimeException ex) {
			log.warn("Could not push the invitation change", ex);
		}
	}

}
