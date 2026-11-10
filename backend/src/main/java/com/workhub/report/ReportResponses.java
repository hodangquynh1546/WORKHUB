package com.workhub.report;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportResponses {

	private ReportResponses() {
	}

	/**
	 * One calendar day of a sprint. {@code actual} is false for days with no recorded state (before the first
	 * snapshot or after today / the completion day); the numbers are then null and only the ideal line applies.
	 */
	public record ChartDay(LocalDate date, boolean actual, double idealPoints, double idealIssues, Integer scopePoints,
			Integer donePoints, Integer remainingPoints, Integer scopeIssues, Integer todoIssues,
			Integer inProgressIssues, Integer doneIssues, Integer remainingIssues) {
	}

	/** Data for burndown, burnup and the sprint's cumulative flow; {@code started} is false before the sprint starts. */
	public record SprintCharts(UUID sprintId, String name, String status, boolean started, LocalDate startDate,
			LocalDate endDate, int workdays, Integer committedPoints, Integer committedIssues, List<ChartDay> days) {
	}

	public record VelocitySprint(UUID sprintId, String name, LocalDate endDate, int committedPoints,
			int completedPoints, int committedIssues, int completedIssues) {
	}

	/** {@code average} is the mean completed points of the last (up to) three sprints, null with no history. */
	public record Velocity(List<VelocitySprint> sprints, Double average) {
	}

	public record FlowPoint(UUID issueId, String key, String title, LocalDate doneDate, double leadDays,
			Double cycleDays) {
	}

	/** Days from creation (lead) and from first start (cycle) to done; cycle values skip issues never started. */
	public record FlowSummary(int count, Double avgLead, Double medianLead, Double p85Lead, Double avgCycle,
			Double medianCycle, Double p85Cycle) {
	}

	public record LeadTime(int days, FlowSummary summary, List<FlowPoint> points) {
	}

	public record WorkloadRow(UUID userId, String fullName, String avatarUrl, int todoIssues, int inProgressIssues,
			int openPoints) {
	}

	public record Workload(List<WorkloadRow> rows) {
	}

	public record BugWeek(LocalDate weekStart, int created, int resolved, int open) {
	}

	public record BugTrend(List<BugWeek> weeks) {
	}

}
