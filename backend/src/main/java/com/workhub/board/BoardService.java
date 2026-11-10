package com.workhub.board;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.issue.Issue;
import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueRepository;
import com.workhub.project.Methodology;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintMapper;
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardService {

	/** On a Kanban board, finished work stays visible for this long. */
	private static final Duration KANBAN_DONE_VISIBLE = Duration.ofDays(14);

	private final AccessService access;

	private final WorkflowService workflow;

	private final SprintRepository sprints;

	private final SprintMapper sprintMapper;

	private final IssueRepository issues;

	private final IssueMapper issueMapper;

	private final Clock clock;

	public BoardService(AccessService access, WorkflowService workflow, SprintRepository sprints,
			SprintMapper sprintMapper, IssueRepository issues, IssueMapper issueMapper, Clock clock) {
		this.access = access;
		this.workflow = workflow;
		this.sprints = sprints;
		this.sprintMapper = sprintMapper;
		this.issues = issues;
		this.issueMapper = issueMapper;
		this.clock = clock;
	}

	public BoardResponse get(UUID userId, UUID projectId) {
		ProjectAccess caller = access.project(userId, projectId);
		Methodology methodology = caller.project().getMethodology();
		Sprint sprint = null;
		List<Issue> cards = List.of();
		if (methodology == Methodology.SCRUM) {
			sprint = sprints.findFirstByProjectIdAndStatus(projectId, SprintStatus.ACTIVE).orElse(null);
			if (sprint != null) {
				cards = issues.findAllBySprintIdOrderByBacklogRankAscIdAsc(sprint.getId());
			}
		}
		else {
			cards = issues.findBoardIssues(projectId, clock.instant().minus(KANBAN_DONE_VISIBLE));
		}
		return new BoardResponse(methodology, (sprint == null) ? null : sprintMapper.toResponse(sprint),
				workflow.columns(projectId), issueMapper.toResponses(caller.project(), cards, false),
				caller.permissions());
	}

}
