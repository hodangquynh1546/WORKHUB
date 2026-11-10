package com.workhub.board;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-project names and visibility of the five fixed statuses. Projects start with every status enabled under its
 * default name; a row is only stored once the project customises it. Arbitrary custom statuses are not supported.
 */
@Service
@Transactional
public class WorkflowService {

	private static final Map<IssueStatus, String> DEFAULT_NAMES = new EnumMap<>(Map.of(IssueStatus.TO_DO, "Cần làm",
			IssueStatus.IN_PROGRESS, "Đang làm", IssueStatus.CODE_REVIEW, "Code review", IssueStatus.TESTING,
			"Kiểm thử", IssueStatus.DONE, "Hoàn thành"));

	private static final Set<IssueStatus> OPTIONAL = Set.of(IssueStatus.CODE_REVIEW, IssueStatus.TESTING);

	private final AccessService access;

	private final WorkflowStatusRepository statuses;

	private final IssueRepository issues;

	public WorkflowService(AccessService access, WorkflowStatusRepository statuses, IssueRepository issues) {
		this.access = access;
		this.statuses = statuses;
		this.issues = issues;
	}

	@Transactional(readOnly = true)
	public List<WorkflowEntry> get(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		return load(projectId);
	}

	public List<WorkflowEntry> update(UUID userId, UUID projectId, List<WorkflowEntry> entries) {
		access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT).requireActive();
		Map<IssueStatus, WorkflowEntry> byStatus = new EnumMap<>(IssueStatus.class);
		for (WorkflowEntry entry : entries) {
			if (byStatus.put(entry.status(), entry) != null) {
				throw ApiException.badRequest("INVALID_WORKFLOW", "Each status may appear once");
			}
		}
		if (byStatus.size() != IssueStatus.values().length) {
			throw ApiException.badRequest("INVALID_WORKFLOW", "All five statuses must be given");
		}
		Set<String> names = new HashSet<>();
		for (IssueStatus status : IssueStatus.values()) {
			WorkflowEntry entry = byStatus.get(status);
			if (!entry.enabled() && !OPTIONAL.contains(status)) {
				throw ApiException.badRequest("INVALID_WORKFLOW", status + " cannot be disabled");
			}
			if (entry.enabled() && !names.add(entry.name().strip().toLowerCase(Locale.ROOT))) {
				throw ApiException.badRequest("DUPLICATE_STATUS_NAME", "Two columns share the name " + entry.name());
			}
			if (!entry.enabled() && issues.countByProjectIdAndStatus(projectId, status) > 0) {
				throw ApiException.conflict("STATUS_IN_USE", "Move the issues out of " + status + " before hiding it");
			}
		}
		List<WorkflowStatus> rows = new ArrayList<>();
		for (IssueStatus status : IssueStatus.values()) {
			WorkflowEntry entry = byStatus.get(status);
			WorkflowStatus row = statuses.findById(new WorkflowStatusKey(projectId, status))
				.orElseGet(() -> new WorkflowStatus(projectId, status, entry.name(), entry.enabled()));
			row.update(entry.name().strip(), entry.enabled());
			rows.add(row);
		}
		statuses.saveAll(rows);
		return load(projectId);
	}

	/** Used when an issue is moved: hidden statuses cannot be chosen. */
	@Transactional(readOnly = true)
	public void requireEnabled(UUID projectId, IssueStatus status) {
		if (load(projectId).stream().noneMatch((entry) -> entry.status() == status && entry.enabled())) {
			throw ApiException.badRequest("STATUS_DISABLED", "This project does not use the status " + status);
		}
	}

	/** Enabled columns only, in workflow order. */
	@Transactional(readOnly = true)
	public List<WorkflowEntry> columns(UUID projectId) {
		return load(projectId).stream().filter(WorkflowEntry::enabled).toList();
	}

	private List<WorkflowEntry> load(UUID projectId) {
		Map<IssueStatus, WorkflowEntry> custom = new EnumMap<>(IssueStatus.class);
		for (Object[] row : statuses.findRows(projectId)) {
			custom.put((IssueStatus) row[0], new WorkflowEntry((IssueStatus) row[0], (String) row[1], (Boolean) row[2]));
		}
		List<WorkflowEntry> result = new ArrayList<>();
		for (IssueStatus status : IssueStatus.values()) {
			result.add(custom.getOrDefault(status, new WorkflowEntry(status, DEFAULT_NAMES.get(status), true)));
		}
		return result;
	}

}
