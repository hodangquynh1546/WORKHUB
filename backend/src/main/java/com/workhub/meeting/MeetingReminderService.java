package com.workhub.meeting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.workhub.notification.NotificationService;
import com.workhub.notification.NotificationType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends "meeting starts soon" notifications to the organizer and everyone invited who did not decline, once per
 * meeting and start time (moving the meeting reminds again). Run every minute by the scheduler.
 */
@Service
@Transactional
public class MeetingReminderService {

	/** Longest reminder the API accepts (a week), so only meetings starting within it can be due. */
	private static final Duration HORIZON = Duration.ofMinutes(10080);

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM");

	private final MeetingRepository meetings;

	private final MeetingAttendeeRepository attendees;

	private final NotificationService notifications;

	private final Clock clock;

	private final ZoneId zone;

	MeetingReminderService(MeetingRepository meetings, MeetingAttendeeRepository attendees,
			NotificationService notifications, Clock clock, ZoneId zone) {
		this.meetings = meetings;
		this.attendees = attendees;
		this.notifications = notifications;
		this.clock = clock;
		this.zone = zone;
	}

	/** Returns how many meetings were reminded about. */
	public int run() {
		Instant now = clock.instant();
		int reminded = 0;
		for (Meeting m : meetings.findDueForReminder(now, now.plus(HORIZON))) {
			if (now.isBefore(m.getStartsAt().minus(Duration.ofMinutes(m.getReminderMinutes())))) {
				continue;
			}
			Set<UUID> audience = attendees.findAllByMeetingId(m.getId())
				.stream()
				.filter((a) -> a.getResponse() != AttendeeResponse.DECLINED)
				.map(MeetingAttendee::getUserId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			audience.add(m.getOrganizerId());
			notifications.notifyAll(audience, NotificationType.MEETING_REMINDER, "Sắp họp: " + m.getTitle(),
					"Bắt đầu lúc " + m.getStartsAt().atZone(zone).format(WHEN), "/projects/" + m.getProjectId() + "/meetings/" + m.getId(),
					m.getProjectId(), null, "REMINDER:" + m.getId() + ":" + m.getStartsAt().getEpochSecond());
			reminded++;
		}
		return reminded;
	}

}
