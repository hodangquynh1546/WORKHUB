package com.workhub.board;

import java.util.List;
import java.util.Set;

import com.workhub.access.ProjectPermission;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.project.Methodology;
import com.workhub.sprint.SprintResponses.SprintResponse;

/**
 * What the board shows. Scrum: the issues of the active sprint ({@code sprint} is null when none is running).
 * Kanban: the project's open issues plus recently finished ones. {@code columns} are the enabled statuses in order.
 */
public record BoardResponse(Methodology methodology, SprintResponse sprint, List<WorkflowEntry> columns,
		List<IssueResponse> issues, Set<ProjectPermission> permissions) {
}
