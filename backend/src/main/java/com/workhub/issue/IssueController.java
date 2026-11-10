package com.workhub.issue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.common.PageResponse;
import com.workhub.issue.IssueResponses.ActivityResponse;
import com.workhub.issue.IssueResponses.IssueDetailResponse;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueResponses.ProjectStats;
import com.workhub.security.CurrentUser;

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
public class IssueController {

	private final IssueService issueService;

	public IssueController(IssueService issueService) {
		this.issueService = issueService;
	}

	@PostMapping("/api/projects/{projectId}/issues")
	@ResponseStatus(HttpStatus.CREATED)
	public IssueResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody IssueRequests.Create request) {
		return issueService.create(CurrentUser.id(jwt), projectId, request);
	}

	/** Filters are all optional; repeat {@code type}, {@code status} or {@code priority} to match any of several. */
	@GetMapping("/api/projects/{projectId}/issues")
	public PageResponse<IssueResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) String q, @RequestParam(required = false) List<IssueType> type,
			@RequestParam(required = false) List<IssueStatus> status,
			@RequestParam(required = false) List<IssuePriority> priority,
			@RequestParam(required = false) UUID assigneeId, @RequestParam(defaultValue = "false") boolean unassigned,
			@RequestParam(required = false) UUID epicId, @RequestParam(required = false) String label,
			@RequestParam(required = false) RefinementStatus refinement,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		IssueFilter filter = new IssueFilter(q, type, status, priority, assigneeId, unassigned, epicId, label,
				refinement);
		return issueService.list(CurrentUser.id(jwt), projectId, filter, sort, page, size);
	}

	@GetMapping("/api/projects/{projectId}/issue-labels")
	public List<String> labels(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return issueService.labels(CurrentUser.id(jwt), projectId);
	}

	/** {@code today} lets the browser decide which calendar day it is (overdue is date-based). */
	@GetMapping("/api/projects/{projectId}/stats")
	public ProjectStats stats(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
		return issueService.stats(CurrentUser.id(jwt), projectId, today);
	}

	@GetMapping("/api/issues/{issueId}")
	public IssueDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId) {
		return issueService.get(CurrentUser.id(jwt), issueId);
	}

	@PutMapping("/api/issues/{issueId}")
	public IssueResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@Valid @RequestBody IssueRequests.Update request) {
		return issueService.update(CurrentUser.id(jwt), issueId, request);
	}

	@PutMapping("/api/issues/{issueId}/status")
	public IssueResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@Valid @RequestBody IssueRequests.ChangeStatus request) {
		return issueService.changeStatus(CurrentUser.id(jwt), issueId, request.status());
	}

	@PutMapping("/api/issues/{issueId}/assignee")
	public IssueResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@RequestBody IssueRequests.Assign request) {
		return issueService.assign(CurrentUser.id(jwt), issueId, request.assigneeId());
	}

	@PutMapping("/api/issues/{issueId}/rank")
	public IssueResponse rank(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@RequestBody IssueRequests.Rank request) {
		return issueService.rank(CurrentUser.id(jwt), issueId, request.afterIssueId());
	}

	@DeleteMapping("/api/issues/{issueId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId) {
		issueService.delete(CurrentUser.id(jwt), issueId);
	}

	@GetMapping("/api/issues/{issueId}/activity")
	public List<ActivityResponse> activity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId) {
		return issueService.history(CurrentUser.id(jwt), issueId);
	}

}
