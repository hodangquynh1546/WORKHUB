package com.workhub.sprint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.ProjectPermission;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueResponses.UserRef;

public final class SprintResponses {

	private SprintResponses() {
	}

	/**
	 * {@code issueCount}, {@code scopePoints}, {@code doneIssueCount} and {@code donePoints} describe the issues
	 * currently in the sprint (for a completed sprint: the finished ones). {@code committed*} are frozen at start,
	 * {@code completed*} at completion.
	 */
	public record SprintResponse(UUID id, UUID projectId, String name, String goal, LocalDate startDate,
			LocalDate endDate, SprintStatus status, UserRef facilitator, Integer capacityPoints,
			Integer committedPoints, Integer committedIssues, Integer completedPoints, Integer completedIssues,
			GoalOutcome goalOutcome, String goalNote, Instant startedAt, Instant completedAt, Instant createdAt,
			int issueCount, int scopePoints, int doneIssueCount, int donePoints) {
	}

	/**
	 * Progress numbers of a sprint. For FUTURE/ACTIVE sprints they come from the issues as they are now; for closed
	 * ones from the recorded outcome. {@code added*}/{@code removed*} are scope changes after the sprint started.
	 */
	public record SprintDashboard(int totalIssues, int todoIssues, int inProgressIssues, int doneIssues,
			int blockedIssues, int overdueIssues, int scopePoints, int donePoints, int remainingPoints,
			Integer committedPoints, int addedPoints, int addedIssues, int removedPoints, int removedIssues) {
	}

	public record SprintDetail(SprintResponse sprint, SprintDashboard dashboard, List<IssueResponse> issues,
			Set<ProjectPermission> permissions) {
	}

	public record SnapshotResponse(LocalDate date, int scopePoints, int donePoints, int scopeIssues, int todoIssues,
			int inProgressIssues, int doneIssues) {
	}

	public record SprintReport(SprintResponse sprint, SprintDashboard dashboard, List<IssueResponse> doneIssues,
			List<IssueResponse> unfinishedIssues, List<IssueResponse> removedIssues,
			List<SnapshotResponse> snapshots) {
	}

}
