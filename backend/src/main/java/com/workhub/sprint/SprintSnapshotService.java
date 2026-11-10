package com.workhub.sprint;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueChangedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps one row per active sprint and calendar day holding the sprint's state at the last change of that day.
 * Burndown/burnup charts are drawn from these rows, so they must be written whenever the sprint's numbers move.
 */
@Service
@Transactional
public class SprintSnapshotService {

	private final SprintRepository sprints;

	private final SprintSnapshotRepository snapshots;

	private final SprintStats stats;

	private final Clock clock;

	private final ZoneId zone;

	SprintSnapshotService(SprintRepository sprints, SprintSnapshotRepository snapshots, SprintStats stats, Clock clock,
			ZoneId zone) {
		this.sprints = sprints;
		this.snapshots = snapshots;
		this.stats = stats;
		this.clock = clock;
		this.zone = zone;
	}

	/** Records today's state if the sprint is active; does nothing otherwise. */
	void capture(Sprint sprint) {
		if (sprint.getStatus() != SprintStatus.ACTIVE) {
			return;
		}
		// Writers of the same sprint line up here, so two requests cannot both insert today's row.
		sprints.lockSprint(sprint.getId());
		LocalDate today = LocalDate.now(clock.withZone(zone));
		SprintSnapshot snapshot = snapshots.findBySprintIdAndDate(sprint.getId(), today)
			.orElseGet(() -> new SprintSnapshot(sprint.getId(), today));
		snapshot.update(stats.counts(sprint.getId()), clock.instant());
		snapshots.save(snapshot);
	}

	@Transactional(readOnly = true)
	List<SprintSnapshot> history(UUID sprintId) {
		return snapshots.findAllBySprintIdOrderByDateAsc(sprintId);
	}

	/** An issue of a sprint changed status/points: refresh the sprint's row for today. */
	@EventListener
	void on(IssueChangedEvent event) {
		sprints.findById(event.sprintId()).ifPresent(this::capture);
	}

}
