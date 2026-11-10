package com.workhub.issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.issue.IssueResponses.IssueRef;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueResponses.SprintRef;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.project.Project;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintRepository;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Component;

/** Turns issues into responses with a fixed number of queries per call (users, ancestors, child counts). */
@Component
public class IssueMapper {

	/** Epic → feature → story/task → sub-task: an epic is at most three levels above an issue. */
	private static final int MAX_ANCESTOR_LEVELS = 3;

	private final UserRepository users;

	private final IssueRepository issues;

	private final SprintRepository sprints;

	IssueMapper(UserRepository users, IssueRepository issues, SprintRepository sprints) {
		this.users = users;
		this.issues = issues;
		this.sprints = sprints;
	}

	public IssueResponse toResponse(Project project, Issue issue, boolean full) {
		return toResponses(project, List.of(issue), full).get(0);
	}

	public List<IssueResponse> toResponses(Project project, List<Issue> list, boolean full) {
		if (list.isEmpty()) {
			return List.of();
		}
		Map<UUID, User> people = loadUsers(list);
		Map<UUID, Issue> ancestors = loadAncestors(list);
		Map<UUID, int[]> counts = loadChildCounts(list);
		Map<UUID, Sprint> sprintsById = loadSprints(list);
		List<IssueResponse> out = new ArrayList<>(list.size());
		for (Issue issue : list) {
			Issue parent = ancestors.get(issue.getParentId());
			int[] count = counts.getOrDefault(issue.getId(), new int[2]);
			out.add(new IssueResponse(issue.getId(), issue.getProjectId(), key(project, issue), issue.getNumber(),
					issue.getType(), issue.getTitle(), full ? issue.getDescription() : null, issue.getPriority(),
					issue.getStatus(), issue.getRefinement(), userRef(people.get(issue.getReporterId())),
					userRef(people.get(issue.getAssigneeId())), (parent != null) ? ref(project, parent) : null,
					nearestEpic(project, issue, ancestors), issue.getStartDate(), issue.getDueDate(),
					issue.getStoryPoints(), issue.getOriginalEstimateMinutes(), issue.getRemainingEstimateMinutes(),
					full ? issue.getAcceptanceCriteria() : null, issue.getLabels().stream().sorted().toList(),
					issue.getBacklogRank(), issue.getStartedAt(), issue.getDoneAt(), issue.getCreatedAt(),
					issue.getUpdatedAt(), count[0], count[1], issue.isBlocked(), issue.getBlockedReason(),
					sprintRef(sprintsById.get(issue.getSprintId()))));
		}
		return out;
	}

	private static SprintRef sprintRef(Sprint sprint) {
		return (sprint == null) ? null : new SprintRef(sprint.getId(), sprint.getName(), sprint.getStatus());
	}

	private Map<UUID, Sprint> loadSprints(List<Issue> list) {
		Set<UUID> ids = new HashSet<>();
		for (Issue issue : list) {
			if (issue.getSprintId() != null) {
				ids.add(issue.getSprintId());
			}
		}
		Map<UUID, Sprint> byId = new HashMap<>();
		if (!ids.isEmpty()) {
			sprints.findAllById(ids).forEach((sprint) -> byId.put(sprint.getId(), sprint));
		}
		return byId;
	}

	static String key(Project project, Issue issue) {
		return project.getProjectKey() + "-" + issue.getNumber();
	}

	static IssueRef ref(Project project, Issue issue) {
		return new IssueRef(issue.getId(), key(project, issue), issue.getTitle(), issue.getType());
	}

	public static UserRef userRef(User user) {
		return (user == null) ? null : new UserRef(user.getId(), user.getFullName(), user.getAvatarUrl());
	}

	private Map<UUID, User> loadUsers(List<Issue> list) {
		Set<UUID> ids = new HashSet<>();
		for (Issue issue : list) {
			ids.add(issue.getReporterId());
			if (issue.getAssigneeId() != null) {
				ids.add(issue.getAssigneeId());
			}
		}
		Map<UUID, User> byId = new HashMap<>();
		users.findAllById(ids).forEach((user) -> byId.put(user.getId(), user));
		return byId;
	}

	/** Parents, grandparents and so on, one batched query per level. */
	private Map<UUID, Issue> loadAncestors(List<Issue> list) {
		Map<UUID, Issue> known = new HashMap<>();
		Set<UUID> frontier = parentIds(list);
		for (int level = 0; level < MAX_ANCESTOR_LEVELS && !frontier.isEmpty(); level++) {
			frontier.removeAll(known.keySet());
			List<Issue> loaded = issues.findAllById(frontier);
			loaded.forEach((issue) -> known.put(issue.getId(), issue));
			frontier = parentIds(loaded);
		}
		return known;
	}

	private static Set<UUID> parentIds(Collection<Issue> list) {
		Set<UUID> ids = new HashSet<>();
		for (Issue issue : list) {
			if (issue.getParentId() != null) {
				ids.add(issue.getParentId());
			}
		}
		return ids;
	}

	private static IssueRef nearestEpic(Project project, Issue issue, Map<UUID, Issue> ancestors) {
		Issue current = ancestors.get(issue.getParentId());
		for (int level = 0; current != null && level < MAX_ANCESTOR_LEVELS; level++) {
			if (current.getType() == IssueType.EPIC) {
				return ref(project, current);
			}
			current = ancestors.get(current.getParentId());
		}
		return null;
	}

	private Map<UUID, int[]> loadChildCounts(List<Issue> list) {
		Map<UUID, int[]> counts = new HashMap<>();
		for (Object[] row : issues.childCounts(list.stream().map(Issue::getId).toList())) {
			counts.put((UUID) row[0], new int[] { ((Number) row[1]).intValue(), ((Number) row[2]).intValue() });
		}
		return counts;
	}

}
