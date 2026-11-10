package com.workhub.report;

import java.util.UUID;

import com.workhub.report.ReportResponses.BugTrend;
import com.workhub.report.ReportResponses.LeadTime;
import com.workhub.report.ReportResponses.SprintCharts;
import com.workhub.report.ReportResponses.Velocity;
import com.workhub.report.ReportResponses.Workload;
import com.workhub.security.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

	private final ReportService reports;

	public ReportController(ReportService reports) {
		this.reports = reports;
	}

	@GetMapping("/api/sprints/{sprintId}/charts")
	public SprintCharts sprintCharts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
		return reports.sprintCharts(CurrentUser.id(jwt), sprintId);
	}

	@GetMapping("/api/projects/{projectId}/reports/velocity")
	public Velocity velocity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return reports.velocity(CurrentUser.id(jwt), projectId);
	}

	@GetMapping("/api/projects/{projectId}/reports/lead-time")
	public LeadTime leadTime(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "90") int days) {
		return reports.leadTime(CurrentUser.id(jwt), projectId, days);
	}

	@GetMapping("/api/projects/{projectId}/reports/workload")
	public Workload workload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return reports.workload(CurrentUser.id(jwt), projectId);
	}

	@GetMapping("/api/projects/{projectId}/reports/bug-trend")
	public BugTrend bugTrend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "8") int weeks) {
		return reports.bugTrend(CurrentUser.id(jwt), projectId, weeks);
	}

}
