package com.workhub.export;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueFilter;
import com.workhub.issue.IssuePriority;
import com.workhub.issue.IssueStatus;
import com.workhub.issue.IssueType;
import com.workhub.issue.RefinementStatus;
import com.workhub.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads. {@code format} is {@code csv} (default) or {@code xlsx}. Charts are exported as images by the browser,
 * which has them; the numbers behind them are exported here ({@code velocity}, sprint {@code daily}, …).
 */
@RestController
public class ExportController {

	private final ExportService exports;

	public ExportController(ExportService exports) {
		this.exports = exports;
	}

	@GetMapping("/api/projects/{projectId}/export/issues")
	public ResponseEntity<byte[]> issues(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format,
			@RequestParam(required = false) String q, @RequestParam(required = false) List<IssueType> type,
			@RequestParam(required = false) List<IssueStatus> status, @RequestParam(required = false) List<IssuePriority> priority,
			@RequestParam(required = false) UUID assigneeId, @RequestParam(defaultValue = "false") boolean unassigned,
			@RequestParam(required = false) UUID sprintId, @RequestParam(defaultValue = "false") boolean unplanned,
			@RequestParam(required = false) UUID epicId, @RequestParam(required = false) String label,
			@RequestParam(required = false) RefinementStatus refinement,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo) {
		IssueFilter filter = new IssueFilter(q, type, status, priority, assigneeId, unassigned, epicId, label, refinement, sprintId, unplanned);
		return exports.issues(CurrentUser.id(jwt), projectId, filter, createdFrom, createdTo, ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/export/members")
	public ResponseEntity<byte[]> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format) {
		return exports.members(CurrentUser.id(jwt), projectId, ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/export/velocity")
	public ResponseEntity<byte[]> velocity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format) {
		return exports.velocity(CurrentUser.id(jwt), projectId, ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/export/lead-time")
	public ResponseEntity<byte[]> leadTime(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format,
			@RequestParam(defaultValue = "90") int days) {
		return exports.leadTime(CurrentUser.id(jwt), projectId, days, ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/export/workload")
	public ResponseEntity<byte[]> workload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format) {
		return exports.workload(CurrentUser.id(jwt), projectId, ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/export/bug-trend")
	public ResponseEntity<byte[]> bugTrend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam(defaultValue = "csv") String format,
			@RequestParam(defaultValue = "8") int weeks) {
		return exports.bugTrend(CurrentUser.id(jwt), projectId, weeks, ExportFormat.parse(format));
	}

	@GetMapping("/api/sprints/{sprintId}/export")
	public ResponseEntity<byte[]> sprintReport(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId, @RequestParam(defaultValue = "csv") String format) {
		return exports.sprintReport(CurrentUser.id(jwt), sprintId, ExportFormat.parse(format));
	}

	@GetMapping("/api/sprints/{sprintId}/export/daily")
	public ResponseEntity<byte[]> sprintDaily(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId, @RequestParam(defaultValue = "csv") String format) {
		return exports.sprintDaily(CurrentUser.id(jwt), sprintId, ExportFormat.parse(format));
	}

	@GetMapping("/api/workspaces/{workspaceId}/export/projects")
	public ResponseEntity<byte[]> workspaceProjects(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId, @RequestParam(defaultValue = "csv") String format) {
		return exports.workspaceProjects(CurrentUser.id(jwt), workspaceId, ExportFormat.parse(format));
	}

	@GetMapping("/api/workspaces/{workspaceId}/export/members")
	public ResponseEntity<byte[]> workspaceMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId, @RequestParam(defaultValue = "csv") String format) {
		return exports.workspaceMembers(CurrentUser.id(jwt), workspaceId, ExportFormat.parse(format));
	}

	@GetMapping("/api/teams/{teamId}/export/projects")
	public ResponseEntity<byte[]> teamProjects(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID teamId, @RequestParam(defaultValue = "csv") String format) {
		return exports.teamProjects(CurrentUser.id(jwt), teamId, ExportFormat.parse(format));
	}

}
