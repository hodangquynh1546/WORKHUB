package com.workhub.report;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.common.ApiException;
import com.workhub.issue.IssueStatus;
import com.workhub.report.ReportResponses.BugTrend;
import com.workhub.report.ReportResponses.BugWeek;
import com.workhub.report.ReportResponses.FlowPoint;
import com.workhub.report.ReportResponses.FlowSummary;
import com.workhub.report.ReportResponses.LeadTime;
import com.workhub.report.ReportResponses.SprintCharts;
import com.workhub.report.ReportResponses.Velocity;
import com.workhub.report.ReportResponses.VelocitySprint;
import com.workhub.report.ReportResponses.Workload;
import com.workhub.report.ReportResponses.WorkloadRow;
import com.workhub.report.ReportRows.BugRow;
import com.workhub.report.ReportRows.DoneRow;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintSnapshotRepository;
import com.workhub.sprint.SprintStatus;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agile reports. Everything is derived from recorded history (daily sprint snapshots, completed-sprint outcomes,
 * issue timestamps), never from "how the issues look right now", except the workload report which is a live view.
 * Any caller who can see the project can read them.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

	private final AccessService access;

	private final SprintRepository sprints;

	private final SprintSnapshotRepository snapshots;

	private final ReportRepository reports;

	private final UserRepository users;

	private final Clock clock;

	private final ZoneId zone;

	ReportService(AccessService access, SprintRepository sprints, SprintSnapshotRepository snapshots,
			ReportRepository reports, UserRepository users, Clock clock, ZoneId zone) {
		this.access = access;
		this.sprints = sprints;
		this.snapshots = snapshots;
		this.reports = reports;
		this.users = users;
		this.clock = clock;
		this.zone = zone;
	}

	public SprintCharts sprintCharts(UUID userId, UUID sprintId) {
		Sprint sprint = sprints.findById(sprintId).orElseThrow(ReportService::sprintNotFound);
		try {
			access.project(userId, sprint.getProjectId());
		}
		catch (ApiException ex) {
			throw (ex.getStatus() == HttpStatus.NOT_FOUND) ? sprintNotFound() : ex;
		}
		LocalDate completedDay = (sprint.getCompletedAt() != null) ? day(sprint.getCompletedAt()) : null;
		return SprintChartBuilder.build(sprint, snapshots.findAllBySprintIdOrderByDateAsc(sprintId), today(),
				completedDay);
	}

	/** The last ten completed sprints, oldest first, and the average of the last three. */
	public Velocity velocity(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		List<Sprint> completed = sprints.findAllByProjectIdAndStatusInOrderByStartDateDescCreatedAtDesc(projectId,
				List.of(SprintStatus.COMPLETED));
		List<VelocitySprint> rows = new ArrayList<>();
		for (Sprint s : completed.stream().limit(10).toList().reversed()) {
			rows.add(new VelocitySprint(s.getId(), s.getName(), s.getEndDate(), nz(s.getCommittedPoints()),
					nz(s.getCompletedPoints()), nz(s.getCommittedIssues()), nz(s.getCompletedIssues())));
		}
		List<VelocitySprint> lastThree = rows.subList(Math.max(0, rows.size() - 3), rows.size());
		Double average = lastThree.isEmpty() ? null
				: round1(lastThree.stream().mapToInt(VelocitySprint::completedPoints).average().orElse(0));
		return new Velocity(rows, average);
	}

	public LeadTime leadTime(UUID userId, UUID projectId, int days) {
		ProjectAccess caller = access.project(userId, projectId);
		int window = Math.clamp(days, 7, 365);
		Instant since = clock.instant().minus(window, ChronoUnit.DAYS);
		String projectKey = caller.project().getProjectKey();
		List<FlowPoint> points = new ArrayList<>();
		for (DoneRow row : reports.doneSince(projectId, since)) {
			double lead = daysBetween(row.createdAt(), row.doneAt());
			Double cycle = (row.startedAt() != null) ? daysBetween(row.startedAt(), row.doneAt()) : null;
			points.add(new FlowPoint(row.id(), projectKey + "-" + row.number(), row.title(), day(row.doneAt()), lead,
					cycle));
		}
		List<Double> leads = points.stream().map(FlowPoint::leadDays).toList();
		List<Double> cycles = points.stream().map(FlowPoint::cycleDays).filter(java.util.Objects::nonNull).toList();
		FlowSummary summary = new FlowSummary(points.size(), average(leads), percentile(leads, 50),
				percentile(leads, 85), average(cycles), percentile(cycles, 50), percentile(cycles, 85));
		return new LeadTime(window, summary, points);
	}

	/** Open (not done) work per assignee, heaviest first; unassigned work is the row with a null user. */
	public Workload workload(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		record Acc(int todo, int inProgress, int points) {
		}
		Map<UUID, Acc> byUser = new HashMap<>();
		UUID unassigned = new UUID(0, 0);
		for (Object[] row : reports.openWorkload(projectId)) {
			UUID assignee = (row[0] != null) ? (UUID) row[0] : unassigned;
			IssueStatus.Category category = ((IssueStatus) row[1]).category();
			int count = ((Number) row[2]).intValue();
			int points = ((Number) row[3]).intValue();
			Acc acc = byUser.getOrDefault(assignee, new Acc(0, 0, 0));
			byUser.put(assignee, new Acc(acc.todo() + (category == IssueStatus.Category.TO_DO ? count : 0),
					acc.inProgress() + (category == IssueStatus.Category.IN_PROGRESS ? count : 0),
					acc.points() + points));
		}
		Map<UUID, User> people = new HashMap<>();
		users.findAllById(byUser.keySet().stream().filter((id) -> !id.equals(unassigned)).toList())
			.forEach((u) -> people.put(u.getId(), u));
		List<WorkloadRow> rows = new ArrayList<>();
		byUser.forEach((id, acc) -> {
			User u = people.get(id);
			rows.add(new WorkloadRow(id.equals(unassigned) ? null : id, (u != null) ? u.getFullName() : null,
					(u != null) ? u.getAvatarUrl() : null, acc.todo(), acc.inProgress(), acc.points()));
		});
		rows.sort(Comparator.comparingInt((WorkloadRow r) -> r.todoIssues() + r.inProgressIssues()).reversed());
		return new Workload(rows);
	}

	/** Bugs created and resolved per week (weeks start on Monday) and how many were still open at each week's end. */
	public BugTrend bugTrend(UUID userId, UUID projectId, int weeks) {
		access.project(userId, projectId);
		int count = Math.clamp(weeks, 2, 26);
		LocalDate thisWeek = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		List<BugRow> bugs = reports.bugs(projectId);
		List<BugWeek> out = new ArrayList<>();
		for (int i = count - 1; i >= 0; i--) {
			LocalDate from = thisWeek.minusWeeks(i);
			LocalDate to = from.plusDays(6);
			int created = 0, resolved = 0, open = 0;
			for (BugRow bug : bugs) {
				LocalDate createdDay = day(bug.createdAt());
				LocalDate doneDay = (bug.doneAt() != null) ? day(bug.doneAt()) : null;
				if (!createdDay.isBefore(from) && !createdDay.isAfter(to)) {
					created++;
				}
				if (doneDay != null && !doneDay.isBefore(from) && !doneDay.isAfter(to)) {
					resolved++;
				}
				if (!createdDay.isAfter(to) && (doneDay == null || doneDay.isAfter(to))) {
					open++;
				}
			}
			out.add(new BugWeek(from, created, resolved, open));
		}
		return new BugTrend(out);
	}

	// ---- helpers ----

	private LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	private LocalDate day(Instant instant) {
		return instant.atZone(zone).toLocalDate();
	}

	private static double daysBetween(Instant from, Instant to) {
		return round1(Math.max(0, seconds(from, to)) / 86_400.0);
	}

	private static double seconds(Instant from, Instant to) {
		return java.time.Duration.between(from, to).toSeconds();
	}

	private static Double average(List<Double> values) {
		return values.isEmpty() ? null : round1(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
	}

	/** Nearest-rank percentile. */
	private static Double percentile(List<Double> values, int percent) {
		if (values.isEmpty()) {
			return null;
		}
		List<Double> sorted = values.stream().sorted().toList();
		int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
		return sorted.get(Math.max(0, rank - 1));
	}

	private static double round1(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static int nz(Integer value) {
		return (value != null) ? value : 0;
	}

	private static ApiException sprintNotFound() {
		return ApiException.notFound("SPRINT_NOT_FOUND", "Sprint not found");
	}

}
