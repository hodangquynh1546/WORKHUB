package com.workhub.audit;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectPermission;
import com.workhub.access.WorkspacePermission;
import com.workhub.common.PageResponse;
import com.workhub.export.ExportFormat;
import com.workhub.export.Exports;
import com.workhub.export.Table;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the audit log. Workspace owners and admins (VIEW_AUDIT) read their workspace's log, project managers
 * (UPDATE_PROJECT) their project's, everyone their own actions. Nothing here can change an entry.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

	private static final int EXPORT_LIMIT = 10_000;

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

	private final AccessService access;

	private final AuditRepository entries;

	private final Clock clock;

	private final ZoneId zone;

	AuditService(AccessService access, AuditRepository entries, Clock clock, ZoneId zone) {
		this.access = access;
		this.entries = entries;
		this.clock = clock;
		this.zone = zone;
	}

	public PageResponse<AuditResponse> workspaceLog(UUID userId, UUID workspaceId, AuditQuery query, int page, int size) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.VIEW_AUDIT);
		return page(query.toSpecification(inWorkspace(workspaceId)), page, size);
	}

	public PageResponse<AuditResponse> projectLog(UUID userId, UUID projectId, AuditQuery query, int page, int size) {
		access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT);
		return page(query.toSpecification((root, q, cb) -> cb.equal(root.get("projectId"), projectId)), page, size);
	}

	public PageResponse<AuditResponse> mine(UUID userId, int page, int size) {
		return page(new AuditQuery(null, null, null, null, null, null, null)
			.toSpecification((root, q, cb) -> cb.equal(root.get("actorId"), userId)), page, size);
	}

	/** The workspace log as a table (newest first, at most 10,000 rows). */
	public ResponseEntity<byte[]> exportWorkspaceLog(UUID userId, UUID workspaceId, AuditQuery query, ExportFormat format) {
		access.workspace(userId, workspaceId).require(WorkspacePermission.VIEW_AUDIT);
		List<AuditEntry> rows = entries.findAll(query.toSpecification(inWorkspace(workspaceId)), PageRequest.of(0, EXPORT_LIMIT, NEWEST_FIRST))
			.getContent();
		Table table = Table.of("Audit log",
				List.of("Thời gian", "Người thực hiện", "Hành động", "Đối tượng", "Tên đối tượng", "Chi tiết", "Kết quả", "IP"),
				rows.stream()
					.map((e) -> List.<Object>of(nz(e.getCreatedAt()), nz(e.getActorName()), e.getAction(), nz(e.getTargetType()),
							nz(e.getTargetLabel()), nz(e.getDetail()), e.getOutcome(), nz(e.getIp())))
					.toList());
		return Exports.download(table, format, "audit-log", LocalDate.now(clock.withZone(zone)));
	}

	private static String nz(Object value) {
		return (value == null) ? "" : value.toString();
	}

	private static Specification<AuditEntry> inWorkspace(UUID workspaceId) {
		return (root, q, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
	}

	private PageResponse<AuditResponse> page(Specification<AuditEntry> spec, int page, int size) {
		Page<AuditEntry> result = entries.findAll(spec, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200), NEWEST_FIRST));
		return PageResponse.of(result, result.getContent().stream().map(AuditResponse::of).toList());
	}

}
