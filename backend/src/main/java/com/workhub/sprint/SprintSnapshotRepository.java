package com.workhub.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintSnapshotRepository extends JpaRepository<SprintSnapshot, UUID> {

	Optional<SprintSnapshot> findBySprintIdAndDate(UUID sprintId, LocalDate date);

	List<SprintSnapshot> findAllBySprintIdOrderByDateAsc(UUID sprintId);

}
