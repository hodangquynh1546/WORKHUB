package com.workhub.project;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	/** Projects with member count and the user's project and team roles; visibility is filtered by the caller. */
	String ROWS = """
			select new com.workhub.project.ProjectRow(p.id, p.workspaceId, p.teamId, t.name, p.projectKey, p.name,
				p.description, p.avatarUrl, p.startDate, p.endDate, p.methodology, p.status, p.createdAt,
				(select count(pm) from ProjectMember pm where pm.projectId = p.id),
				(select my.role from ProjectMember my where my.projectId = p.id and my.userId = :userId),
				(select tm.role from TeamMember tm where tm.teamId = p.teamId and tm.userId = :userId))
			from Project p join Team t on t.id = p.teamId
			""";

	/**
	 * Atomically takes the next issue number. The UPDATE locks the project row until the transaction ends, so
	 * concurrent creators queue up here; read the value back with {@link #readIssueCounter}. Clears the
	 * persistence context, so call it before loading entities you intend to modify.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Project p set p.issueCounter = p.issueCounter + 1 where p.id = :id")
	int incrementIssueCounter(@Param("id") UUID id);

	@Query("select p.issueCounter from Project p where p.id = :id")
	int readIssueCounter(@Param("id") UUID id);

	boolean existsByWorkspaceIdAndProjectKey(UUID workspaceId, String projectKey);

	@Query(ROWS + " where p.workspaceId = :workspaceId order by lower(p.name)")
	List<ProjectRow> findRowsInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

	@Query(ROWS + " where p.teamId = :teamId order by lower(p.name)")
	List<ProjectRow> findRowsInTeam(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

}
