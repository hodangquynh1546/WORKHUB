package com.workhub.issue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

	List<Issue> findAllByParentIdOrderByBacklogRankAscIdAsc(UUID parentId);

	boolean existsByParentId(UUID parentId);

	Optional<Issue> findByIdAndProjectId(UUID id, UUID projectId);

	@Query("select coalesce(max(i.backlogRank), 0) from Issue i where i.projectId = :projectId")
	long maxRank(@Param("projectId") UUID projectId);

	@Query("select min(i.backlogRank) from Issue i where i.projectId = :projectId and i.id <> :excludeId")
	Optional<Long> minRankExcluding(@Param("projectId") UUID projectId, @Param("excludeId") UUID excludeId);

	@Query("select min(i.backlogRank) from Issue i where i.projectId = :projectId and i.id <> :excludeId and i.backlogRank > :rank")
	Optional<Long> nextRankAfter(@Param("projectId") UUID projectId, @Param("excludeId") UUID excludeId,
			@Param("rank") long rank);

	List<Issue> findAllByProjectIdOrderByBacklogRankAscIdAsc(UUID projectId);

	/** Number of direct children and how many of them are DONE, per parent. Rows: parentId, total, done. */
	@Query("""
			select i.parentId, count(i), sum(case when i.status = com.workhub.issue.IssueStatus.DONE then 1 else 0 end)
			from Issue i where i.parentId in :parentIds group by i.parentId
			""")
	List<Object[]> childCounts(@Param("parentIds") Collection<UUID> parentIds);

	/** Rows: status, count, sum of story points (null-safe). EPICs are containers and are not counted. */
	@Query("""
			select i.status, count(i), coalesce(sum(i.storyPoints), 0) from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC group by i.status
			""")
	List<Object[]> statusTotals(@Param("projectId") UUID projectId);

	@Query("""
			select count(i) from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status <> com.workhub.issue.IssueStatus.DONE and i.dueDate < :today
			""")
	long countOverdue(@Param("projectId") UUID projectId, @Param("today") java.time.LocalDate today);

	@Query("select distinct l from Issue i join i.labels l where i.projectId = :projectId order by l")
	List<String> findLabels(@Param("projectId") UUID projectId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Issue i set i.assigneeId = null where i.projectId = :projectId and i.assigneeId = :userId")
	int unassign(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Issue i set i.assigneeId = null where i.assigneeId = :userId and i.projectId in (select p.id from Project p where p.workspaceId = :workspaceId)")
	int unassignAllInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

}
