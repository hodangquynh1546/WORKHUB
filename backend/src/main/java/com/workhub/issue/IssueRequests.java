package com.workhub.issue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IssueRequests {

	private IssueRequests() {
	}

	/**
	 * Everything but {@code type} and {@code title} is optional. {@code remainingEstimateMinutes} defaults to the
	 * original estimate. Labels are trimmed and lower-cased.
	 */
	public record Create(@NotNull IssueType type, @NotBlank @Size(max = 255) String title,
			@Size(max = 10000) String description, IssuePriority priority, UUID parentId, UUID assigneeId,
			LocalDate startDate, LocalDate dueDate, @Min(0) @Max(100) Integer storyPoints,
			@Min(0) @Max(600000) Integer originalEstimateMinutes, @Min(0) @Max(600000) Integer remainingEstimateMinutes,
			@Size(max = 10000) String acceptanceCriteria, @Size(max = 10) List<@NotBlank @Size(max = 30) String> labels) {
	}

	/**
	 * Replaces every editable field (a null clears it). Status, assignee and backlog position have their own
	 * endpoints.
	 */
	public record Update(@NotNull IssueType type, @NotBlank @Size(max = 255) String title,
			@Size(max = 10000) String description, @NotNull IssuePriority priority, @NotNull RefinementStatus refinement,
			UUID parentId, LocalDate startDate, LocalDate dueDate, @Min(0) @Max(100) Integer storyPoints,
			@Min(0) @Max(600000) Integer originalEstimateMinutes, @Min(0) @Max(600000) Integer remainingEstimateMinutes,
			@Size(max = 10000) String acceptanceCriteria, @Size(max = 10) List<@NotBlank @Size(max = 30) String> labels) {
	}

	public record ChangeStatus(@NotNull IssueStatus status) {
	}

	/** {@code assigneeId} null unassigns. */
	public record Assign(UUID assigneeId) {
	}

	/** Moves the issue directly after {@code afterIssueId}; null moves it to the top of the backlog. */
	public record Rank(UUID afterIssueId) {
	}

	public record CommentBody(@NotBlank @Size(max = 5000) String body) {
	}

	public record ChecklistAdd(@NotBlank @Size(max = 500) String text) {
	}

	/** Only the fields that are present change. */
	public record ChecklistUpdate(@Size(min = 1, max = 500) String text, Boolean done) {
	}

}
