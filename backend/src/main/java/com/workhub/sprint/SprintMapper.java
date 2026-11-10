package com.workhub.sprint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueRepository;
import com.workhub.sprint.SprintResponses.SnapshotResponse;
import com.workhub.sprint.SprintResponses.SprintResponse;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Component;

@Component
public class SprintMapper {

	private final UserRepository users;

	private final IssueRepository issues;

	SprintMapper(UserRepository users, IssueRepository issues) {
		this.users = users;
		this.issues = issues;
	}

	public SprintResponse toResponse(Sprint sprint) {
		return toResponses(List.of(sprint)).get(0);
	}

	/** Two queries for any number of sprints: facilitators and per-sprint issue totals. */
	public List<SprintResponse> toResponses(List<Sprint> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Set<UUID> facilitatorIds = list.stream().map(Sprint::getFacilitatorId).filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, User> people = new HashMap<>();
		users.findAllById(facilitatorIds).forEach((user) -> people.put(user.getId(), user));
		Map<UUID, long[]> totals = new HashMap<>();
		for (Object[] row : issues.sprintSummaries(list.stream().map(Sprint::getId).toList())) {
			totals.put((UUID) row[0], new long[] { ((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
					((Number) row[3]).longValue(), ((Number) row[4]).longValue() });
		}
		return list.stream().map((sprint) -> {
			long[] t = totals.getOrDefault(sprint.getId(), new long[4]);
			return new SprintResponse(sprint.getId(), sprint.getProjectId(), sprint.getName(), sprint.getGoal(),
					sprint.getStartDate(), sprint.getEndDate(), sprint.getStatus(),
					IssueMapper.userRef(people.get(sprint.getFacilitatorId())), sprint.getCapacityPoints(),
					sprint.getCommittedPoints(), sprint.getCommittedIssues(), sprint.getCompletedPoints(),
					sprint.getCompletedIssues(), sprint.getGoalOutcome(), sprint.getGoalNote(), sprint.getStartedAt(),
					sprint.getCompletedAt(), sprint.getCreatedAt(), (int) t[0], (int) t[1], (int) t[2], (int) t[3]);
		}).toList();
	}

	static SnapshotResponse toResponse(SprintSnapshot s) {
		return new SnapshotResponse(s.getDate(), s.getScopePoints(), s.getDonePoints(), s.getScopeIssues(),
				s.getTodoIssues(), s.getInProgressIssues(), s.getDoneIssues());
	}

}
