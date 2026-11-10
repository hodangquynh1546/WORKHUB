package com.workhub.audit;

import java.time.Instant;
import java.util.UUID;

import com.workhub.common.PageResponse;
import com.workhub.export.ExportFormat;
import com.workhub.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

	private final AuditService audit;

	public AuditController(AuditService audit) {
		this.audit = audit;
	}

	@GetMapping("/api/workspaces/{workspaceId}/audit-log")
	public PageResponse<AuditResponse> workspaceLog(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
			@RequestParam(required = false) UUID projectId, @RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) String outcome, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return audit.workspaceLog(CurrentUser.id(jwt), workspaceId, new AuditQuery(actorId, action, projectId, q, from, to, outcome), page, size);
	}

	@GetMapping("/api/workspaces/{workspaceId}/audit-log/export")
	public ResponseEntity<byte[]> exportWorkspaceLog(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId,
			@RequestParam(defaultValue = "csv") String format, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String action, @RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) String outcome) {
		return audit.exportWorkspaceLog(CurrentUser.id(jwt), workspaceId, new AuditQuery(actorId, action, projectId, q, from, to, outcome),
				ExportFormat.parse(format));
	}

	@GetMapping("/api/projects/{projectId}/audit-log")
	public PageResponse<AuditResponse> projectLog(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return audit.projectLog(CurrentUser.id(jwt), projectId, new AuditQuery(actorId, action, null, q, from, to, null), page, size);
	}

	/** The caller's own recorded actions, sign-ins included. */
	@GetMapping("/api/me/audit-log")
	public PageResponse<AuditResponse> mine(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size) {
		return audit.mine(CurrentUser.id(jwt), page, size);
	}

}
