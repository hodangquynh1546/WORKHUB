package com.workhub.meeting;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.common.ApiException;
import com.workhub.meeting.CalendarRows.DueRow;
import com.workhub.meeting.MeetingResponses.Calendar;
import com.workhub.meeting.MeetingResponses.CalendarEvent;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project calendar: meetings, sprints (as date ranges) and issue due dates in one list, for a window of at most
 * 100 days. Days are calendar days in {@code workhub.time-zone}; the browser should ask for a slightly wider window
 * than it shows. {@code personId} narrows meetings to ones that person organizes or attends and due dates to their issues.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

	private static final long MAX_DAYS = 100;

	private final AccessService access;

	private final MeetingRepository meetings;

	private final SprintRepository sprints;

	private final CalendarRepository dueDates;

	private final ZoneId zone;

	CalendarService(AccessService access, MeetingRepository meetings, SprintRepository sprints,
			CalendarRepository dueDates, ZoneId zone) {
		this.access = access;
		this.meetings = meetings;
		this.sprints = sprints;
		this.dueDates = dueDates;
		this.zone = zone;
	}

	public Calendar calendar(UUID userId, UUID projectId, LocalDate from, LocalDate to, UUID personId) {
		ProjectAccess caller = access.project(userId, projectId);
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE", "Ask for 1 to " + MAX_DAYS + " days at a time");
		}
		List<CalendarEvent> events = new ArrayList<>();

		MeetingFilter filter = new MeetingFilter(from.atStartOfDay(zone).toInstant(),
				to.plusDays(1).atStartOfDay(zone).toInstant(), null, null, personId, null);
		for (Meeting m : meetings.findAll(filter.toSpecification(projectId),
				org.springframework.data.domain.Sort.by("startsAt", "id"))) {
			events.add(new CalendarEvent("MEETING", m.getId(), m.getTitle(), m.getStartsAt(), m.getEndsAt(), null, null,
					m.getStatus().name(), m.getType().name()));
		}
		for (Sprint s : sprints.findAllByProjectIdOrderByStartDateDescCreatedAtDesc(projectId)) {
			if (s.getStatus() != SprintStatus.CANCELLED && !s.getStartDate().isAfter(to) && !s.getEndDate().isBefore(from)) {
				events.add(new CalendarEvent("SPRINT", s.getId(), s.getName(), null, null, s.getStartDate(), s.getEndDate(),
						s.getStatus().name(), null));
			}
		}
		String key = caller.project().getProjectKey();
		List<DueRow> due = (personId == null) ? dueDates.dueBetween(projectId, from, to)
				: dueDates.dueBetweenFor(projectId, from, to, personId);
		for (DueRow row : due) {
			events.add(new CalendarEvent("DUE_DATE", row.id(), key + "-" + row.number() + " " + row.title(), null, null,
					row.dueDate(), row.dueDate(), row.status().name(), null));
		}
		return new Calendar(events);
	}

}
