package com.workhub.team;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, UUID> {

	/** Teams the user is on, archived ones excluded. */
	@Query("select t from Team t where t.archivedAt is null and t.id in (select m.teamId from TeamMember m where m.userId = :userId)")
	List<Team> findOfMember(@Param("userId") UUID userId);

	boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

	boolean existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(UUID workspaceId, String name, UUID id);

	/** Teams of a workspace with their member count and the given user's role (null when not on the team). */
	@Query("""
			select new com.workhub.team.TeamRow(t.id, t.workspaceId, t.name, t.description, t.archivedAt, t.createdAt,
				(select count(tm) from TeamMember tm where tm.teamId = t.id),
				(select my.role from TeamMember my where my.teamId = t.id and my.userId = :userId))
			from Team t
			where t.workspaceId = :workspaceId
				and ((:archived = true and t.archivedAt is not null) or (:archived = false and t.archivedAt is null))
			order by lower(t.name)
			""")
	List<TeamRow> findRows(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId,
			@Param("archived") boolean archived);

}
