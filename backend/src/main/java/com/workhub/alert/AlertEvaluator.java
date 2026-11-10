package com.workhub.alert;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.workhub.alert.AlertRows.IssueRow;
import com.workhub.alert.AlertSettings.Values;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueStatus;
import com.workhub.notification.NotificationType;
import com.workhub.project.Project;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.project.ProjectRole;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintIssueRepository;
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintStatus;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The progress rules of a project, evaluated against its thresholds. Pure reading: it never notifies, so the same
 * result feeds both the "risks" card in the UI and the scheduled notification job. Rules switched off (0) give nothing.
 */
@Component
@Transactional(readOnly = true)
class AlertEvaluator {

	private static final List<ProjectRole> LEADERS = List.of(ProjectRole.PROJECT_MANAGER, ProjectRole.SCRUM_MASTER,
			ProjectRole.PRODUCT_OWNER);

	private final AlertRepository alerts;

	private final AlertSettingsService settings;

	private final ProjectMemberRepository members;

	private final SprintRepository sprints;

	private final SprintIssueRepository sprintIssues;

	private final IssueRepository issues;

	AlertEvaluator(AlertRepository alerts, AlertSettingsService settings, ProjectMemberRepository members,
			SprintRepository sprints, SprintIssueRepository sprintIssues, IssueRepository issues) {
		this.alerts = alerts;
		this.settings = settings;
		this.members = members;
		this.sprints = sprints;
		this.sprintIssues = sprintIssues;
		this.issues = issues;
	}

	List<AlertItem> evaluate(Project project, LocalDate today, Instant now) {
		UUID projectId = project.getId();
		Values rules = settings.valuesOf(projectId);
		Set<UUID> leaders = new LinkedHashSet<>(members.findUserIdsByRole(projectId, LEADERS));
		String key = project.getProjectKey();
		List<AlertItem> out = new ArrayList<>();

		// Overdue and due soon share one query: everything due within the soon-window, overdue ones included.
		int soon = Math.max(0, rules.dueSoonDays());
		if (rules.overdueEnabled() || soon > 0) {
			for (IssueRow row : alerts.dueUntil(projectId, today.plusDays(soon))) {
				String name = key + "-" + row.number() + " " + row.title();
				String link = "/issues/" + row.id();
				if (row.dueDate().isBefore(today)) {
					if (rules.overdueEnabled()) {
						long late = ChronoUnit.DAYS.between(row.dueDate(), today);
						out.add(new AlertItem(NotificationType.ALERT_OVERDUE, "CRITICAL", name + " đã quá hạn",
								"Hạn " + row.dueDate() + ", trễ " + late + " ngày", link, row.id(), owners(row, leaders),
								"OVERDUE:" + row.id() + ":" + row.dueDate()));
					}
				}
				else if (soon > 0) {
					long left = ChronoUnit.DAYS.between(today, row.dueDate());
					out.add(new AlertItem(NotificationType.ALERT_DUE_SOON, "WARNING", name + " sắp đến hạn",
							(left == 0) ? "Hạn hôm nay" : "Còn " + left + " ngày (hạn " + row.dueDate() + ")", link, row.id(),
							owners(row, leaders), "DUESOON:" + row.id() + ":" + row.dueDate()));
				}
			}
		}

		if (rules.noProgressDays() > 0) {
			Instant before = now.minus(Duration.ofDays(rules.noProgressDays()));
			for (IssueRow row : alerts.staleInProgress(projectId, before)) {
				long idle = ChronoUnit.DAYS.between(row.updatedAt(), now);
				out.add(new AlertItem(NotificationType.ALERT_NO_PROGRESS, "WARNING",
						key + "-" + row.number() + " không có cập nhật " + idle + " ngày", row.title(), "/issues/" + row.id(), row.id(),
						owners(row, leaders), "NOPROGRESS:" + row.id() + ":" + row.updatedAt().truncatedTo(ChronoUnit.DAYS)));
			}
		}

		if (rules.blockedEnabled()) {
			for (IssueRow row : alerts.blocked(projectId)) {
				Set<UUID> to = new LinkedHashSet<>(leaders);
				if (row.assigneeId() != null) {
					to.add(row.assigneeId());
				}
				out.add(new AlertItem(NotificationType.TASK_BLOCKED, "WARNING", key + "-" + row.number() + " đang bị chặn",
						row.title(), "/issues/" + row.id(), row.id(), to, "BLOCKED:" + row.id() + ":" + today));
			}
		}

		if (rules.overloadOpenIssues() > 0) {
			for (Object[] row : alerts.openByAssignee(projectId)) {
				int open = ((Number) row[1]).intValue();
				if (open > rules.overloadOpenIssues()) {
					UUID user = (UUID) row[0];
					Set<UUID> to = new LinkedHashSet<>(leaders);
					to.add(user);
					out.add(new AlertItem(NotificationType.ALERT_OVERLOAD, "WARNING", "Một thành viên đang giữ " + open + " việc chưa xong",
							"Ngưỡng cảnh báo là " + rules.overloadOpenIssues() + " việc mỗi người", "/projects/" + projectId + "/board",
							user, to, "OVERLOAD:" + user + ":" + projectId + ":" + today));
				}
			}
		}

		sprints.findFirstByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)
			.ifPresent((sprint) -> out.addAll(sprintAlerts(project, sprint, rules, leaders, today)));
		return out;
	}

	private List<AlertItem> sprintAlerts(Project project, Sprint sprint, Values rules, Set<UUID> leaders, LocalDate today) {
		List<AlertItem> out = new ArrayList<>();
		String link = "/projects/" + project.getId() + "/sprints/" + sprint.getId();
		int scope = 0, done = 0, scopeIssues = 0, doneIssues = 0;
		for (Object[] row : issues.sprintStatusTotals(sprint.getId())) {
			int count = ((Number) row[1]).intValue();
			int points = ((Number) row[2]).intValue();
			scope += points;
			scopeIssues += count;
			if (row[0] == IssueStatus.DONE) {
				done += points;
				doneIssues += count;
			}
		}
		// Story points when the sprint has them, otherwise plain issue counts.
		boolean byPoints = scope > 0;
		int total = byPoints ? scope : scopeIssues;
		int finished = byPoints ? done : doneIssues;
		long daysLeft = ChronoUnit.DAYS.between(today, sprint.getEndDate());

		if (rules.sprintRiskOpenPct() > 0 && total > 0 && daysLeft <= rules.sprintRiskDaysLeft()) {
			int openPct = (int) Math.round((total - finished) * 100.0 / total);
			if (openPct >= rules.sprintRiskOpenPct()) {
				String when = (daysLeft < 0) ? "đã quá hạn kết thúc " + (-daysLeft) + " ngày" : "còn " + daysLeft + " ngày";
				out.add(new AlertItem(NotificationType.ALERT_SPRINT_AT_RISK, "WARNING",
						sprint.getName() + " có nguy cơ không đạt mục tiêu: " + when + ", " + openPct + "% chưa xong",
						"Còn " + (total - finished) + (byPoints ? " điểm" : " việc") + " chưa hoàn thành"
								+ ((sprint.getGoal() != null) ? ". Mục tiêu: " + sprint.getGoal() : "") + ". Hãy kiểm tra cùng team.",
						link, sprint.getId(), leaders, "SPRINTRISK:" + sprint.getId() + ":" + today));
			}
		}

		Integer committed = sprint.getCommittedPoints();
		if (rules.scopeChangePct() > 0 && committed != null && committed > 0) {
			Object[] added = sprintIssues.addedAfterStart(sprint.getId()).get(0);
			int addedPoints = ((Number) added[0]).intValue();
			int pct = (int) Math.round(addedPoints * 100.0 / committed);
			if (pct >= rules.scopeChangePct()) {
				// One notification per 10% step, so a growing scope speaks up again without repeating every hour.
				out.add(new AlertItem(NotificationType.ALERT_SCOPE_CHANGE, "INFO",
						sprint.getName() + " đã thêm " + addedPoints + " điểm sau khi bắt đầu (+" + pct + "%)",
						"Cam kết ban đầu là " + committed + " điểm", link, sprint.getId(), leaders,
						"SCOPE:" + sprint.getId() + ":" + (pct / 10)));
			}
		}
		return out;
	}

	/** The assignee; for unassigned work, the people who lead the project. */
	private static Set<UUID> owners(IssueRow row, Set<UUID> leaders) {
		return (row.assigneeId() != null) ? Set.of(row.assigneeId()) : leaders;
	}

}
