package com.workhub.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	String VIEW = """
			select new com.workhub.invitation.InvitationResponse(i.id, i.kind, i.email, i.workspaceId, w.name,
				i.teamId, t.name, i.role, u.fullName, i.status, i.expiresAt, i.createdAt)
			from Invitation i
				join Workspace w on w.id = i.workspaceId
				left join Team t on t.id = i.teamId
				join User u on u.id = i.invitedBy
			""";

	String PENDING = " i.status = com.workhub.invitation.InvitationStatus.PENDING and i.expiresAt > :now ";

	Optional<Invitation> findByTokenHash(String tokenHash);

	List<Invitation> findAllByWorkspaceIdAndTeamIdAndKindAndEmailAndStatus(UUID workspaceId, UUID teamId,
			InvitationKind kind, String email, InvitationStatus status);

	@Query(VIEW + " where i.id = :id")
	InvitationResponse findView(@Param("id") UUID id);

	/** Pending invitations of a workspace, including those into its teams. */
	@Query(VIEW + " where i.workspaceId = :workspaceId and" + PENDING + "order by i.createdAt desc")
	List<InvitationResponse> findPendingInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);

	@Query(VIEW + " where i.teamId = :teamId and" + PENDING + "order by i.createdAt desc")
	List<InvitationResponse> findPendingInTeam(@Param("teamId") UUID teamId, @Param("now") Instant now);

	/** Pending email invitations addressed to {@code email}. */
	@Query(VIEW + " where i.kind = com.workhub.invitation.InvitationKind.EMAIL and i.email = :email and" + PENDING
			+ "order by i.createdAt desc")
	List<InvitationResponse> findPendingForEmail(@Param("email") String email, @Param("now") Instant now);

}
