package com.workhub.issue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.ProjectPermission;
import com.workhub.sprint.SprintStatus;

/** Response shapes of the issue API. */
public final class IssueResponses {

	private IssueResponses() {
	}

	public record UserRef(UUID id, String fullName, String avatarUrl) {
	}

	public record IssueRef(UUID id, String key, String title, IssueType type) {
	}

	public record SprintRef(UUID id, String name, SprintStatus status) {
	}

	/**
	 * {@code description} and {@code acceptanceCriteria} are only filled in detail views (null in lists).
	 * {@code epic} is the nearest EPIC ancestor; {@code childCount}/{@code childDoneCount} count direct children.
	 */
	public record IssueResponse(UUID id, UUID projectId, String key, int number, IssueType type, String title,
			String description, IssuePriority priority, IssueStatus status, RefinementStatus refinement,
			UserRef reporter, UserRef assignee, IssueRef parent, IssueRef epic, LocalDate startDate,
			LocalDate dueDate, Integer storyPoints, Integer originalEstimateMinutes,
			Integer remainingEstimateMinutes, String acceptanceCriteria, List<String> labels, long rank,
			Instant startedAt, Instant doneAt, Instant createdAt, Instant updatedAt, int childCount,
			int childDoneCount, boolean blocked, String blockedReason, SprintRef sprint, String externalRef) {
	}

	public record ChecklistItemResponse(UUID id, String text, boolean done) {
	}

	public record IssueDetailResponse(IssueResponse issue, List<IssueResponse> children,
			List<ChecklistItemResponse> checklist, Set<ProjectPermission> permissions) {
	}

	public record CommentResponse(UUID id, UserRef author, String body, Instant createdAt, Instant updatedAt) {
	}

	/** {@code field} is null for CREATED; old/new are display values (empty for long text fields). */
	public record ActivityResponse(UUID id, UserRef actor, IssueActivity.Kind kind, String field, String oldValue,
			String newValue, Instant createdAt) {
	}

	public record ProjectStats(long total, long toDo, long inProgress, long done, long overdue, long totalPoints,
			long donePoints) {
	}

}
