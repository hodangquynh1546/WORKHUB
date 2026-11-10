package com.workhub.meeting;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.issue.Issue;
import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.meeting.MeetingResponses.ActionItemResponse;
import com.workhub.meeting.MeetingResponses.AttendeeInfo;
import com.workhub.meeting.MeetingResponses.IssueRef;
import com.workhub.meeting.MeetingResponses.MeetingDetail;
import com.workhub.meeting.MeetingResponses.MeetingSummary;
import com.workhub.meeting.MeetingResponses.SprintRef;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintRepository;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Entities to responses with a fixed number of queries per call. */
@Component
@Transactional(readOnly = true)
class MeetingMapper {

	private final UserRepository users;

	private final SprintRepository sprints;

	private final IssueRepository issues;

	private final MeetingAttendeeRepository attendees;

	private final MeetingActionItemRepository actionItems;

	MeetingMapper(UserRepository users, SprintRepository sprints, IssueRepository issues,
			MeetingAttendeeRepository attendees, MeetingActionItemRepository actionItems) {
		this.users = users;
		this.sprints = sprints;
		this.issues = issues;
		this.attendees = attendees;
		this.actionItems = actionItems;
	}

	List<MeetingSummary> summaries(List<Meeting> meetings, UUID callerId) {
		if (meetings.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = meetings.stream().map(Meeting::getId).toList();
		Map<UUID, Integer> attendeeCounts = new HashMap<>();
		attendees.countsFor(ids).forEach((r) -> attendeeCounts.put((UUID) r[0], ((Number) r[1]).intValue()));
		Map<UUID, Integer> open = new HashMap<>();
		actionItems.openCountsFor(ids).forEach((r) -> open.put((UUID) r[0], ((Number) r[1]).intValue()));
		Map<UUID, AttendeeResponse> mine = new HashMap<>();
		attendees.findAllByMeetingIdIn(ids)
			.stream()
			.filter((a) -> a.getUserId().equals(callerId))
			.forEach((a) -> mine.put(a.getMeetingId(), a.getResponse()));
		Map<UUID, UserRef> people = userRefs(meetings.stream().map(Meeting::getOrganizerId).toList());
		Map<UUID, SprintRef> sprintRefs = sprintRefs(meetings.stream().map(Meeting::getSprintId).toList());
		return meetings.stream()
			.map((m) -> new MeetingSummary(m.getId(), m.getTitle(), m.getType(), m.getStatus(), m.getStartsAt(),
					m.getEndsAt(), m.getLocation(), m.getOnlineLink(), sprintRefs.get(m.getSprintId()),
					people.get(m.getOrganizerId()), attendeeCounts.getOrDefault(m.getId(), 0),
					open.getOrDefault(m.getId(), 0), mine.get(m.getId())))
			.toList();
	}

	MeetingDetail detail(Meeting m, ProjectAccess access) {
		List<MeetingAttendee> attendeeRows = attendees.findAllByMeetingId(m.getId());
		List<MeetingActionItem> items = actionItems.findAllByMeetingIdOrderByCreatedAtAscIdAsc(m.getId());
		Set<UUID> ids = new HashSet<>(List.of(m.getOrganizerId(), m.getCreatedBy()));
		attendeeRows.forEach((a) -> ids.add(a.getUserId()));
		items.forEach((i) -> {
			if (i.getAssigneeId() != null) {
				ids.add(i.getAssigneeId());
			}
		});
		Map<UUID, UserRef> people = userRefs(ids);
		Map<UUID, Issue> issueById = new HashMap<>();
		issues.findAllById(items.stream().map(MeetingActionItem::getIssueId).filter(java.util.Objects::nonNull).toList())
			.forEach((i) -> issueById.put(i.getId(), i));
		String key = access.project().getProjectKey();
		Set<ProjectPermission> permissions = access.permissions();
		boolean manage = permissions.contains(ProjectPermission.MANAGE_MEETINGS) && !access.project().isArchived();
		boolean canEdit = manage
				&& (m.getOrganizerId().equals(access.userId()) || permissions.contains(ProjectPermission.UPDATE_PROJECT));
		AttendeeResponse mine = attendeeRows.stream()
			.filter((a) -> a.getUserId().equals(access.userId()))
			.map(MeetingAttendee::getResponse)
			.findFirst()
			.orElse(null);
		return new MeetingDetail(m.getId(), m.getProjectId(), m.getTitle(), m.getType(), m.getStatus(), m.getStartsAt(),
				m.getEndsAt(), m.getLocation(), m.getOnlineLink(), m.getAgenda(), m.getDescription(), m.getMinutes(),
				m.getMinutesRevision(), m.getReminderMinutes(), sprintRefs(java.util.Arrays.asList(m.getSprintId())).get(m.getSprintId()),
				people.get(m.getOrganizerId()), people.get(m.getCreatedBy()),
				attendeeRows.stream()
					.map((a) -> new AttendeeInfo(people.get(a.getUserId()), a.getResponse()))
					.sorted(java.util.Comparator.comparing((AttendeeInfo a) -> a.user() == null ? "" : a.user().fullName()))
					.toList(),
				items.stream()
					.map((i) -> new ActionItemResponse(i.getId(), i.getText(), people.get(i.getAssigneeId()), i.getDueDate(),
							i.getStatus(), issueRef(issueById.get(i.getIssueId()), key)))
					.toList(),
				mine, canEdit, manage);
	}

	Map<UUID, UserRef> userRefs(Collection<UUID> ids) {
		Map<UUID, UserRef> out = new HashMap<>();
		for (User user : users.findAllById(new HashSet<>(ids))) {
			out.put(user.getId(), IssueMapper.userRef(user));
		}
		return out;
	}

	private Map<UUID, SprintRef> sprintRefs(Collection<UUID> ids) {
		Map<UUID, SprintRef> out = new HashMap<>();
		Set<UUID> wanted = new HashSet<>(ids);
		wanted.remove(null);
		for (Sprint sprint : sprints.findAllById(wanted)) {
			out.put(sprint.getId(), new SprintRef(sprint.getId(), sprint.getName()));
		}
		return out;
	}

	private static IssueRef issueRef(Issue issue, String projectKey) {
		return (issue == null) ? null : new IssueRef(issue.getId(), projectKey + "-" + issue.getNumber(), issue.getTitle());
	}

}
