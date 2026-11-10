package com.workhub.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MeetingRequests {

	public static final int MAX_MINUTES = 100_000;

	private MeetingRequests() {
	}

	/**
	 * Everything the organizer edits about a meeting. {@code organizerId} defaults to the caller; the organizer and
	 * attendees must be project members. {@code attendeeIds} replaces the list (replies of people who stay are kept).
	 */
	public record Save(@NotBlank @Size(max = 200) String title, @NotNull MeetingType type, UUID sprintId,
			UUID organizerId, @NotNull Instant startsAt, @NotNull Instant endsAt, @Size(max = 255) String location,
			@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String onlineLink,
			@Size(max = 20000) String agenda, @Size(max = 5000) String description,
			@Min(0) @Max(10080) Integer reminderMinutes, @Size(max = 100) List<UUID> attendeeIds) {
	}

	public record Minutes(@NotNull @Size(max = MAX_MINUTES) String minutes, @NotNull Integer baseRevision) {
	}

	public record Respond(@NotNull AttendeeResponse response) {
	}

	public record ActionItem(@NotBlank @Size(max = 500) String text, UUID assigneeId, LocalDate dueDate) {
	}

	public record UpdateActionItem(@NotBlank @Size(max = 500) String text, UUID assigneeId, LocalDate dueDate,
			@NotNull ActionItemStatus status) {
	}

}
