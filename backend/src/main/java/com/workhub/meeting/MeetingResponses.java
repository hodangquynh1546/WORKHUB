package com.workhub.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueResponses.UserRef;

public final class MeetingResponses {

	private MeetingResponses() {
	}

	public record SprintRef(UUID id, String name) {
	}

	public record MeetingSummary(UUID id, String title, MeetingType type, MeetingStatus status, Instant startsAt,
			Instant endsAt, String location, String onlineLink, SprintRef sprint, UserRef organizer, int attendeeCount,
			int openActionItems, AttendeeResponse myResponse) {
	}

	public record AttendeeInfo(UserRef user, AttendeeResponse response) {
	}

	/** {@code issue} is set once the item was turned into a task. */
	public record IssueRef(UUID id, String key, String title) {
	}

	public record ActionItemResponse(UUID id, String text, UserRef assignee, LocalDate dueDate, ActionItemStatus status,
			IssueRef issue) {
	}

	/**
	 * {@code canEdit}: change details, cancel or delete (organizer or project manager); {@code canTakeNotes}: write
	 * minutes and manage action items; {@code myResponse} is null unless the caller is an attendee.
	 */
	public record MeetingDetail(UUID id, UUID projectId, String title, MeetingType type, MeetingStatus status,
			Instant startsAt, Instant endsAt, String location, String onlineLink, String agenda, String description,
			String minutes, int minutesRevision, Integer reminderMinutes, SprintRef sprint, UserRef organizer,
			UserRef createdBy, List<AttendeeInfo> attendees, List<ActionItemResponse> actionItems,
			AttendeeResponse myResponse, boolean canEdit, boolean canTakeNotes) {
	}

	/** One entry of the project calendar; timed events carry instants, all-day ones carry dates. */
	public record CalendarEvent(String kind, UUID id, String title, Instant startsAt, Instant endsAt,
			LocalDate startDate, LocalDate endDate, String status, String detail) {
	}

	public record Calendar(List<CalendarEvent> events) {
	}

}
