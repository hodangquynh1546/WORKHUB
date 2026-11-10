package com.workhub.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.workhub.report.ReportResponses.ChartDay;
import com.workhub.report.ReportResponses.SprintCharts;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintSnapshot;

/**
 * Turns the recorded daily snapshots into one row per calendar day: days without a snapshot carry the last known
 * state forward, and the ideal line runs over working days (Mon-Fri) from the scope committed at the start to zero
 * at the end date. Snapshots are end-of-day states, so day 1's ideal point is already one working day of progress.
 */
final class SprintChartBuilder {

	private SprintChartBuilder() {
	}

	static SprintCharts build(Sprint sprint, List<SprintSnapshot> snapshots, LocalDate today, LocalDate completedDay) {
		if (sprint.getStartedAt() == null || snapshots.isEmpty()) {
			return new SprintCharts(sprint.getId(), sprint.getName(), sprint.getStatus().name(), false,
					sprint.getStartDate(), sprint.getEndDate(), workdays(sprint.getStartDate(), sprint.getEndDate()),
					null, null, List.of());
		}
		LocalDate start = sprint.getStartDate();
		LocalDate end = sprint.getEndDate();
		SprintSnapshot first = snapshots.get(0);
		LocalDate lastActual = (completedDay != null) ? completedDay : max(today, first.getDate());
		LocalDate axisStart = min(start, first.getDate());
		LocalDate axisEnd = max(end, lastActual);

		int openingPoints = (sprint.getCommittedPoints() != null) ? sprint.getCommittedPoints() : first.getScopePoints();
		int openingIssues = (sprint.getCommittedIssues() != null) ? sprint.getCommittedIssues() : first.getScopeIssues();
		int totalWorkdays = Math.max(1, workdays(start, end));

		List<ChartDay> days = new ArrayList<>();
		SprintSnapshot current = null;
		int next = 0;
		for (LocalDate d = axisStart; !d.isAfter(axisEnd); d = d.plusDays(1)) {
			while (next < snapshots.size() && !snapshots.get(next).getDate().isAfter(d)) {
				current = snapshots.get(next++);
			}
			double share = idealShare(d, start, end, totalWorkdays);
			double idealPoints = round1(openingPoints * share);
			double idealIssues = round1(openingIssues * share);
			boolean actual = current != null && !d.isAfter(lastActual);
			days.add(actual
					? new ChartDay(d, true, idealPoints, idealIssues, current.getScopePoints(), current.getDonePoints(),
							current.getScopePoints() - current.getDonePoints(), current.getScopeIssues(),
							current.getTodoIssues(), current.getInProgressIssues(), current.getDoneIssues(),
							current.getScopeIssues() - current.getDoneIssues())
					: new ChartDay(d, false, idealPoints, idealIssues, null, null, null, null, null, null, null, null));
		}
		return new SprintCharts(sprint.getId(), sprint.getName(), sprint.getStatus().name(), true, start, end,
				totalWorkdays, sprint.getCommittedPoints(), sprint.getCommittedIssues(), days);
	}

	/** Fraction of the opening scope still remaining on the ideal line at the end of day {@code d}. */
	private static double idealShare(LocalDate d, LocalDate start, LocalDate end, int totalWorkdays) {
		if (d.isBefore(start)) {
			return 1;
		}
		if (d.isAfter(end)) {
			return 0;
		}
		return Math.max(0, totalWorkdays - workdays(start, d)) / (double) totalWorkdays;
	}

	/** Working days (Mon-Fri) in [from, to], both inclusive. */
	static int workdays(LocalDate from, LocalDate to) {
		int count = 0;
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
				count++;
			}
		}
		return count;
	}

	private static double round1(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
