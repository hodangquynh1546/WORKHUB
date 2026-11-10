package com.workhub.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.security.CurrentUser;
import com.workhub.sprint.SprintResponses.SnapshotResponse;
import com.workhub.sprint.SprintResponses.SprintDetail;
import com.workhub.sprint.SprintResponses.SprintReport;
import com.workhub.sprint.SprintResponses.SprintResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SprintController {

	private final SprintService sprintService;

	public SprintController(SprintService sprintService) {
		this.sprintService = sprintService;
	}

	@PostMapping("/api/projects/{projectId}/sprints")
	@ResponseStatus(HttpStatus.CREATED)
	public SprintResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody SprintRequests.Save request) {
		return sprintService.create(CurrentUser.id(jwt), projectId, request);
	}

	@GetMapping("/api/projects/{projectId}/sprints")
	public List<SprintResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) List<SprintStatus> status) {
		return sprintService.list(CurrentUser.id(jwt), projectId, status);
	}

	/** {@code today} lets the browser decide which calendar day it is (overdue is date-based). */
	@GetMapping("/api/sprints/{sprintId}")
	public SprintDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
		return sprintService.get(CurrentUser.id(jwt), sprintId, today);
	}

	@PutMapping("/api/sprints/{sprintId}")
	public SprintResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
			@Valid @RequestBody SprintRequests.Save request) {
		return sprintService.update(CurrentUser.id(jwt), sprintId, request);
	}

	@PostMapping("/api/sprints/{sprintId}/issues")
	public SprintDetail addIssues(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
			@Valid @RequestBody SprintRequests.AddIssues request) {
		return sprintService.addIssues(CurrentUser.id(jwt), sprintId, request);
	}

	@DeleteMapping("/api/sprints/{sprintId}/issues/{issueId}")
	public SprintDetail removeIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
			@PathVariable UUID issueId, @RequestParam(defaultValue = "false") boolean includeChildren) {
		return sprintService.removeIssue(CurrentUser.id(jwt), sprintId, issueId, includeChildren);
	}

	@PostMapping("/api/sprints/{sprintId}/start")
	public SprintDetail start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
		return sprintService.start(CurrentUser.id(jwt), sprintId);
	}

	@PostMapping("/api/sprints/{sprintId}/complete")
	public SprintDetail complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
			@Valid @RequestBody SprintRequests.Complete request) {
		return sprintService.complete(CurrentUser.id(jwt), sprintId, request);
	}

	@PostMapping("/api/sprints/{sprintId}/cancel")
	public SprintResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
		return sprintService.cancel(CurrentUser.id(jwt), sprintId);
	}

	@GetMapping("/api/sprints/{sprintId}/report")
	public SprintReport report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
		return sprintService.report(CurrentUser.id(jwt), sprintId);
	}

	@GetMapping("/api/sprints/{sprintId}/snapshots")
	public List<SnapshotResponse> snapshots(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
		return sprintService.history(CurrentUser.id(jwt), sprintId);
	}

}
