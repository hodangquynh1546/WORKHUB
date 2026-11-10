package com.workhub.sprint;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

	List<Sprint> findAllByProjectIdOrderByStartDateDescCreatedAtDesc(UUID projectId);

	List<Sprint> findAllByProjectIdAndStatusInOrderByStartDateDescCreatedAtDesc(UUID projectId,
			Collection<SprintStatus> statuses);

	Optional<Sprint> findFirstByProjectIdAndStatus(UUID projectId, SprintStatus status);

	boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<SprintStatus> statuses);

	boolean existsByIdAndStatusIn(UUID id, Collection<SprintStatus> statuses);

	/** Serializes sprint starts within a project: the row lock is held until the transaction ends. */
	@Query(value = "select id from projects where id = :projectId for update", nativeQuery = true)
	List<Object> lockProject(@Param("projectId") UUID projectId);

	/** Serializes writers of one sprint's snapshot row. */
	@Query(value = "select id from sprints where id = :sprintId for update", nativeQuery = true)
	List<Object> lockSprint(@Param("sprintId") UUID sprintId);

}
