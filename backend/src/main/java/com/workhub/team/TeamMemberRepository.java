package com.workhub.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

	Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

	boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

	long countByTeamId(UUID teamId);

	@Query("""
			select new com.workhub.team.TeamMemberResponse(u.id, u.email, u.fullName, u.avatarUrl, m.role, m.joinedAt)
			from TeamMember m join User u on u.id = m.userId
			where m.teamId = :teamId
			order by m.joinedAt
			""")
	List<TeamMemberResponse> findMembers(@Param("teamId") UUID teamId);

	/** Drops the user from every team of the workspace (used when they leave or are removed from it). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TeamMember m where m.userId = :userId and m.teamId in (select t.id from Team t where t.workspaceId = :workspaceId)")
	int deleteAllInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

}
