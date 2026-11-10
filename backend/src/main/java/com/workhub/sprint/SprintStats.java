package com.workhub.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueStatus;
import com.workhub.sprint.SprintResponses.SprintDashboard;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Numbers about a sprint: live counts over its issues and the dashboard built from them or from the recorded outcome. */
@Component
@Transactional(readOnly = true)
public class SprintStats {

	private final IssueRepository issues;

	private final SprintIssueRepository members;

	SprintStats(IssueRepository issues, SprintIssueRepository members) {
		this.issues = issues;
		this.members = members;
	}

	/** Counts over the issues in the sprint right now. */
	SprintCounts counts(UUID sprintId) {
		int total = 0, todo = 0, inProgress = 0, done = 0, scope = 0, donePoints = 0;
		for (Object[] row : issues.sprintStatusTotals(sprintId)) {
			IssueStatus status = (IssueStatus) row[0];
			int count = ((Number) row[1]).intValue();
			int points = ((Number) row[2]).intValue();
			total += count;
			scope += points;
			switch (status.category()) {
				case TO_DO -> todo += count;
				case IN_PROGRESS -> inProgress += count;
				case DONE -> {
					done += count;
					donePoints += points;
				}
			}
		}
		return new SprintCounts(total, todo, inProgress, done, scope, donePoints);
	}

	SprintDashboard dashboard(Sprint sprint, LocalDate today) {
		boolean open = sprint.getStatus().isOpen();
		SprintCounts counts = open ? counts(sprint.getId()) : closedCounts(sprint.getId());
		int blocked = open ? (int) issues.countBlockedInSprint(sprint.getId()) : 0;
		int overdue = open ? (int) issues.countOverdueInSprint(sprint.getId(), today) : 0;
		int addedPoints = 0, addedIssues = 0, removedPoints = 0, removedIssues = 0;
		if (sprint.getStartedAt() != null) {
			Object[] added = members.addedAfterStart(sprint.getId()).get(0);
			addedPoints = ((Number) added[0]).intValue();
			addedIssues = ((Number) added[1]).intValue();
			Object[] removed = members.removedAfterStart(sprint.getId(), sprint.getStartedAt()).get(0);
			removedPoints = ((Number) removed[0]).intValue();
			removedIssues = ((Number) removed[1]).intValue();
		}
		return new SprintDashboard(counts.issues(), counts.todo(), counts.inProgress(), counts.done(), blocked,
				overdue, counts.scopePoints(), counts.donePoints(), counts.remainingPoints(), sprint.getCommittedPoints(),
				addedPoints, addedIssues, removedPoints, removedIssues);
	}

	/** What the sprint ended with: the issues that finished it, plus those carried over, at their final status. */
	private SprintCounts closedCounts(UUID sprintId) {
		int total = 0, todo = 0, inProgress = 0, done = 0, scope = 0, donePoints = 0;
		List<SprintIssue> rows = members.findAllBySprintIdOrderByAddedAtAscIdAsc(sprintId);
		for (SprintIssue row : rows) {
			boolean stayed = row.getRemovedAt() == null || row.getRemovalReason() == RemovalReason.CARRIED_OVER;
			if (!stayed || row.getFinalStatus() == null) {
				continue;
			}
			int points = (row.getFinalPoints() != null) ? row.getFinalPoints() : 0;
			total++;
			scope += points;
			switch (row.getFinalStatus().category()) {
				case TO_DO -> todo++;
				case IN_PROGRESS -> inProgress++;
				case DONE -> {
					done++;
					donePoints += points;
				}
			}
		}
		return new SprintCounts(total, todo, inProgress, done, scope, donePoints);
	}

}
