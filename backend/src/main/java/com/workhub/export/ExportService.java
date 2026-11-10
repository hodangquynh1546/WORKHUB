package com.workhub.export;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.common.ApiException;
import com.workhub.issue.IssueFilter;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueService;
import com.workhub.project.ProjectMemberResponse;
import com.workhub.project.ProjectResponse;
import com.workhub.project.ProjectService;
import com.workhub.report.ReportResponses.BugTrend;
import com.workhub.report.ReportResponses.LeadTime;
import com.workhub.report.ReportResponses.SprintCharts;
import com.workhub.report.ReportResponses.Velocity;
import com.workhub.report.ReportResponses.Workload;
import com.workhub.report.ReportService;
import com.workhub.sprint.SprintResponses.SprintReport;
import com.workhub.sprint.SprintService;
import com.workhub.workspace.WorkspaceMemberRepository;
import com.workhub.workspace.WorkspaceMemberResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data exports as CSV or Excel. Every export goes through the service that already guards that data (issues, sprints,
 * reports, projects), so who may export what is exactly who may read it; nothing here has its own access rules
 * beyond what those services check. Reads are recorded in the audit log by the catalogue.
 */
@Service
@Transactional(readOnly = true)
public class ExportService {

	private static final int ISSUE_LIMIT = 10_000;

	private static final int PAGE = 200;

	private final IssueService issues;

	private final SprintService sprints;

	private final ReportService reports;

	private final ProjectService projects;

	private final AccessService access;

	private final WorkspaceMemberRepository workspaceMembers;

	private final IssueRepository issueRepository;

	private final Clock clock;

	private final ZoneId zone;

	ExportService(IssueService issues, SprintService sprints, ReportService reports, ProjectService projects, AccessService access,
			WorkspaceMemberRepository workspaceMembers, IssueRepository issueRepository, Clock clock, ZoneId zone) {
		this.issues = issues;
		this.sprints = sprints;
		this.reports = reports;
		this.projects = projects;
		this.access = access;
		this.workspaceMembers = workspaceMembers;
		this.issueRepository = issueRepository;
		this.clock = clock;
		this.zone = zone;
	}

	// ---- project ----

	/** Issues matching the backlog filters (optionally created within a window), in backlog order. */
	public ResponseEntity<byte[]> issues(UUID userId, UUID projectId, IssueFilter filter, Instant createdFrom, Instant createdTo,
			ExportFormat format) {
		List<List<Object>> rows = new ArrayList<>();
		for (int page = 0; rows.size() < ISSUE_LIMIT; page++) {
			var result = issues.list(userId, projectId, filter, "rank", page, PAGE);
			for (IssueResponse i : result.items()) {
				if ((createdFrom == null || !i.createdAt().isBefore(createdFrom)) && (createdTo == null || i.createdAt().isBefore(createdTo))) {
					rows.add(issueRow(i));
				}
			}
			if (page + 1 >= result.totalPages()) {
				break;
			}
		}
		return download(Table.of("Công việc", ISSUE_HEADERS, rows), format, "issues");
	}

	public ResponseEntity<byte[]> members(UUID userId, UUID projectId, ExportFormat format) {
		List<ProjectMemberResponse> members = projects.listMembers(userId, projectId);
		List<List<Object>> rows = members.stream()
			.map((m) -> List.<Object>of(m.fullName(), m.email(), m.role().name(), m.joinedAt().toString()))
			.toList();
		return download(Table.of("Thành viên", List.of("Họ tên", "Email", "Vai trò", "Tham gia lúc"), rows), format, "members");
	}

	public ResponseEntity<byte[]> velocity(UUID userId, UUID projectId, ExportFormat format) {
		Velocity v = reports.velocity(userId, projectId);
		List<List<Object>> rows = v.sprints().stream()
			.map((s) -> List.<Object>of(s.name(), s.endDate().toString(), s.committedPoints(), s.completedPoints(), s.committedIssues(), s.completedIssues()))
			.toList();
		return download(Table.of("Velocity", List.of("Sprint", "Kết thúc", "Cam kết (điểm)", "Hoàn thành (điểm)", "Cam kết (việc)", "Hoàn thành (việc)"), rows),
				format, "velocity");
	}

	public ResponseEntity<byte[]> leadTime(UUID userId, UUID projectId, int days, ExportFormat format) {
		LeadTime l = reports.leadTime(userId, projectId, days);
		List<List<Object>> rows = l.points().stream()
			.map((p) -> {
				List<Object> row = new ArrayList<>();
				row.add(p.key());
				row.add(p.title());
				row.add(p.doneDate().toString());
				row.add(p.leadDays());
				row.add(p.cycleDays());
				return row;
			})
			.toList();
		return download(Table.of("Lead time", List.of("Mã", "Tiêu đề", "Ngày xong", "Lead time (ngày)", "Cycle time (ngày)"), rows), format, "lead-time");
	}

	public ResponseEntity<byte[]> workload(UUID userId, UUID projectId, ExportFormat format) {
		Workload w = reports.workload(userId, projectId);
		List<List<Object>> rows = w.rows().stream()
			.map((r) -> List.<Object>of((r.fullName() == null) ? "Chưa giao" : r.fullName(), r.todoIssues(), r.inProgressIssues(), r.openPoints()))
			.toList();
		return download(Table.of("Khối lượng", List.of("Người", "Cần làm", "Đang làm", "Điểm đang mở"), rows), format, "workload");
	}

	public ResponseEntity<byte[]> bugTrend(UUID userId, UUID projectId, int weeks, ExportFormat format) {
		BugTrend b = reports.bugTrend(userId, projectId, weeks);
		List<List<Object>> rows = b.weeks().stream()
			.map((w) -> List.<Object>of(w.weekStart().toString(), w.created(), w.resolved(), w.open()))
			.toList();
		return download(Table.of("Bug", List.of("Tuần bắt đầu", "Phát sinh", "Đã xử lý", "Còn mở cuối tuần"), rows), format, "bug-trend");
	}

	// ---- sprint ----

	/** The sprint report: what finished, what did not, what was removed. */
	public ResponseEntity<byte[]> sprintReport(UUID userId, UUID sprintId, ExportFormat format) {
		SprintReport report = sprints.report(userId, sprintId);
		List<List<Object>> rows = new ArrayList<>();
		add(rows, "Hoàn thành", report.doneIssues());
		add(rows, "Chưa xong", report.unfinishedIssues());
		add(rows, "Đã gỡ khỏi sprint", report.removedIssues());
		return download(Table.of("Sprint " + report.sprint().name(), List.of("Nhóm", "Mã", "Loại", "Tiêu đề", "Trạng thái", "Điểm", "Người phụ trách"), rows),
				format, "sprint-" + report.sprint().name());
	}

	/** Day by day numbers behind the burndown, burnup and cumulative flow charts. */
	public ResponseEntity<byte[]> sprintDaily(UUID userId, UUID sprintId, ExportFormat format) {
		SprintCharts c = reports.sprintCharts(userId, sprintId);
		List<List<Object>> rows = c.days().stream()
			.map((d) -> {
				List<Object> row = new ArrayList<>();
				row.add(d.date().toString());
				row.add(d.idealPoints());
				row.add(d.scopePoints());
				row.add(d.donePoints());
				row.add(d.remainingPoints());
				row.add(d.scopeIssues());
				row.add(d.todoIssues());
				row.add(d.inProgressIssues());
				row.add(d.doneIssues());
				return row;
			})
			.toList();
		return download(Table.of(c.name(), List.of("Ngày", "Lý tưởng (điểm)", "Phạm vi (điểm)", "Hoàn thành (điểm)", "Còn lại (điểm)", "Phạm vi (việc)",
				"Cần làm", "Đang làm", "Hoàn thành"), rows), format, "sprint-daily-" + c.name());
	}

	// ---- workspace and team ----

	/** One row per project the caller can see, with its size and progress. */
	public ResponseEntity<byte[]> workspaceProjects(UUID userId, UUID workspaceId, ExportFormat format) {
		return projectSummary(projects.listForWorkspace(userId, workspaceId), format, "projects");
	}

	public ResponseEntity<byte[]> teamProjects(UUID userId, UUID teamId, ExportFormat format) {
		return projectSummary(projects.listForTeam(userId, teamId), format, "team-projects");
	}

	public ResponseEntity<byte[]> workspaceMembers(UUID userId, UUID workspaceId, ExportFormat format) {
		access.workspace(userId, workspaceId);
		List<WorkspaceMemberResponse> members = workspaceMembers.findMembers(workspaceId);
		List<List<Object>> rows = members.stream()
			.map((m) -> List.<Object>of(m.fullName(), m.email(), m.role().name(), m.joinedAt().toString()))
			.toList();
		return download(Table.of("Thành viên", List.of("Họ tên", "Email", "Vai trò", "Tham gia lúc"), rows), format, "workspace-members");
	}

	private ResponseEntity<byte[]> projectSummary(List<ProjectResponse> list, ExportFormat format, String name) {
		List<List<Object>> rows = new ArrayList<>();
		for (ProjectResponse p : list) {
			long total = 0, done = 0, points = 0, donePoints = 0;
			for (Object[] row : issueRepository.statusTotals(p.id())) {
				long count = ((Number) row[1]).longValue();
				long pts = ((Number) row[2]).longValue();
				total += count;
				points += pts;
				if (row[0] == com.workhub.issue.IssueStatus.DONE) {
					done += count;
					donePoints += pts;
				}
			}
			rows.add(List.of(p.key(), p.name(), p.teamName(), p.methodology().name(), p.status().name(), p.memberCount(), total, done,
					points, donePoints, (p.startDate() == null) ? "" : p.startDate().toString(), (p.endDate() == null) ? "" : p.endDate().toString()));
		}
		return download(Table.of("Dự án", List.of("Mã", "Tên", "Team", "Phương pháp", "Trạng thái", "Thành viên", "Công việc", "Đã xong", "Điểm", "Điểm xong",
				"Bắt đầu", "Kết thúc dự kiến"), rows), format, name);
	}

	// ---- helpers ----

	private static final List<String> ISSUE_HEADERS = List.of("Mã", "Loại", "Tiêu đề", "Trạng thái", "Ưu tiên", "Người phụ trách", "Người báo cáo",
			"Điểm", "Ước lượng (phút)", "Còn lại (phút)", "Sprint", "Epic", "Bắt đầu", "Hạn", "Nhãn", "Bị chặn", "Tạo lúc", "Cập nhật lúc",
			"Hoàn thành lúc");

	private static List<Object> issueRow(IssueResponse i) {
		List<Object> row = new ArrayList<>();
		row.add(i.key());
		row.add(i.type().name());
		row.add(i.title());
		row.add(i.status().name());
		row.add(i.priority().name());
		row.add((i.assignee() == null) ? "" : i.assignee().fullName());
		row.add((i.reporter() == null) ? "" : i.reporter().fullName());
		row.add(i.storyPoints());
		row.add(i.originalEstimateMinutes());
		row.add(i.remainingEstimateMinutes());
		row.add((i.sprint() == null) ? "" : i.sprint().name());
		row.add((i.epic() == null) ? "" : i.epic().key());
		row.add((i.startDate() == null) ? "" : i.startDate().toString());
		row.add((i.dueDate() == null) ? "" : i.dueDate().toString());
		row.add(String.join(", ", i.labels()));
		row.add(i.blocked());
		row.add(i.createdAt().toString());
		row.add(i.updatedAt().toString());
		row.add((i.doneAt() == null) ? "" : i.doneAt().toString());
		return row;
	}

	private static void add(List<List<Object>> rows, String group, List<IssueResponse> list) {
		for (IssueResponse i : list) {
			rows.add(List.of(group, i.key(), i.type().name(), i.title(), i.status().name(), (i.storyPoints() == null) ? "" : i.storyPoints(),
					(i.assignee() == null) ? "" : i.assignee().fullName()));
		}
	}

	private ResponseEntity<byte[]> download(Table table, ExportFormat format, String name) {
		return Exports.download(table, format, name, LocalDate.now(clock.withZone(zone)));
	}

}
