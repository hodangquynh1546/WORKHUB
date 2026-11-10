package com.workhub.sprint;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintIssueRepository extends JpaRepository<SprintIssue, UUID> {

	/** The open stretch of an issue in a sprint, if any. */
	Optional<SprintIssue> findFirstBySprintIdAndIssueIdAndRemovedAtIsNull(UUID sprintId, UUID issueId);

	List<SprintIssue> findAllBySprintIdAndRemovedAtIsNull(UUID sprintId);

	List<SprintIssue> findAllBySprintIdOrderByAddedAtAscIdAsc(UUID sprintId);

	/** Points that joined after the start, and points that left after the start (REMOVED and MOVED only). */
	@Query("select coalesce(sum(si.addedPoints), 0), count(si) from SprintIssue si where si.sprintId = :sprintId and si.addedAfterStart = true")
	List<Object[]> addedAfterStart(@Param("sprintId") UUID sprintId);

	@Query("""
			select coalesce(sum(si.removedPoints), 0), count(si) from SprintIssue si
			where si.sprintId = :sprintId and si.removedAt is not null
				and si.removalReason in (com.workhub.sprint.RemovalReason.REMOVED, com.workhub.sprint.RemovalReason.MOVED)
				and si.removedAt > :startedAt
			""")
	List<Object[]> removedAfterStart(@Param("sprintId") UUID sprintId, @Param("startedAt") java.time.Instant startedAt);

	List<SprintIssue> findAllByIssueIdIn(Collection<UUID> issueIds);

}
