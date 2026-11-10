package com.workhub.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.meeting.MeetingResponses.Calendar;
import com.workhub.meeting.MeetingResponses.MeetingDetail;
import com.workhub.meeting.MeetingResponses.MeetingSummary;
import com.workhub.meeting.MeetingTemplates.Template;
import com.workhub.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingController {

	private final MeetingService meetings;

	private final CalendarService calendar;

	public MeetingController(MeetingService meetings, CalendarService calendar) {
		this.meetings = meetings;
		this.calendar = calendar;
	}

	@GetMapping("/api/meeting-templates")
	public List<Template> templates() {
		return MeetingTemplates.all();
	}

	/**
	 * {@code from}/{@code to} are instants (ISO-8601) selecting meetings that overlap the window; {@code personId}
	 * keeps meetings that person organizes or attends; {@code newestFirst} reverses the order (for past meetings).
	 */
	@GetMapping("/api/projects/{projectId}/meetings")
	public List<MeetingSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) MeetingType type, @RequestParam(required = false) UUID sprintId,
			@RequestParam(required = false) UUID personId, @RequestParam(required = false) List<MeetingStatus> status,
			@RequestParam(defaultValue = "false") boolean newestFirst) {
		return meetings.list(CurrentUser.id(jwt), projectId, new MeetingFilter(from, to, type, sprintId, personId, status),
				newestFirst);
	}

	@PostMapping("/api/projects/{projectId}/meetings")
	@ResponseStatus(HttpStatus.CREATED)
	public MeetingDetail create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody MeetingRequests.Save request) {
		return meetings.create(CurrentUser.id(jwt), projectId, request);
	}

	@GetMapping("/api/meetings/{meetingId}")
	public MeetingDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
		return meetings.get(CurrentUser.id(jwt), meetingId);
	}

	@PutMapping("/api/meetings/{meetingId}")
	public MeetingDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId,
			@Valid @RequestBody MeetingRequests.Save request) {
		return meetings.update(CurrentUser.id(jwt), meetingId, request);
	}

	@PostMapping("/api/meetings/{meetingId}/cancel")
	public MeetingDetail cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
		return meetings.setCancelled(CurrentUser.id(jwt), meetingId, true);
	}

	@PostMapping("/api/meetings/{meetingId}/reopen")
	public MeetingDetail reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
		return meetings.setCancelled(CurrentUser.id(jwt), meetingId, false);
	}

	@DeleteMapping("/api/meetings/{meetingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
		meetings.delete(CurrentUser.id(jwt), meetingId);
	}

	@PutMapping("/api/meetings/{meetingId}/minutes")
	public MeetingDetail saveMinutes(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId,
			@Valid @RequestBody MeetingRequests.Minutes request) {
		return meetings.saveMinutes(CurrentUser.id(jwt), meetingId, request);
	}

	@PutMapping("/api/meetings/{meetingId}/response")
	public MeetingDetail respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId,
			@Valid @RequestBody MeetingRequests.Respond request) {
		return meetings.respond(CurrentUser.id(jwt), meetingId, request.response());
	}

	@PostMapping("/api/meetings/{meetingId}/action-items")
	@ResponseStatus(HttpStatus.CREATED)
	public MeetingDetail addActionItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId,
			@Valid @RequestBody MeetingRequests.ActionItem request) {
		return meetings.addActionItem(CurrentUser.id(jwt), meetingId, request);
	}

	@PutMapping("/api/meeting-action-items/{itemId}")
	public MeetingDetail updateActionItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
			@Valid @RequestBody MeetingRequests.UpdateActionItem request) {
		return meetings.updateActionItem(CurrentUser.id(jwt), itemId, request);
	}

	@DeleteMapping("/api/meeting-action-items/{itemId}")
	public MeetingDetail deleteActionItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
		return meetings.deleteActionItem(CurrentUser.id(jwt), itemId);
	}

	@PostMapping("/api/meeting-action-items/{itemId}/create-task")
	public MeetingDetail createTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
		return meetings.createTask(CurrentUser.id(jwt), itemId);
	}

	/** Window of at most 100 days; dates are calendar days in the server's configured time zone. */
	@GetMapping("/api/projects/{projectId}/calendar")
	public Calendar calendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID personId) {
		return calendar.calendar(CurrentUser.id(jwt), projectId, from, to, personId);
	}

}
