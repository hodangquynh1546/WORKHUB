package com.workhub.meeting;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.common.Strings;
import com.workhub.issue.IssueRequests;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueService;
import com.workhub.issue.IssueType;
import com.workhub.meeting.MeetingResponses.MeetingDetail;
import com.workhub.meeting.MeetingResponses.MeetingSummary;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.sprint.SprintRepository;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meetings of a project. Anyone who can see the project can read; MANAGE_MEETINGS (every role but stakeholders)
 * schedules meetings, takes minutes and manages action items; changing a meeting's details, cancelling or deleting
 * it needs the organizer or a project manager (UPDATE_PROJECT). Writes need a non-archived project.
 */
@Service
@Transactional
public class MeetingService {

	static final Duration MAX_LENGTH = Duration.ofHours(24);

	private static final int LIST_LIMIT = 500;

	private final AccessService access;

	private final MeetingRepository meetings;

	private final MeetingAttendeeRepository attendees;

	private final MeetingActionItemRepository actionItems;

	private final MeetingMapper mapper;

	private final ProjectMemberRepository projectMembers;

	private final SprintRepository sprints;

	private final IssueService issueService;

	private final Clock clock;

	MeetingService(AccessService access, MeetingRepository meetings, MeetingAttendeeRepository attendees,
			MeetingActionItemRepository actionItems, MeetingMapper mapper, ProjectMemberRepository projectMembers,
			SprintRepository sprints, IssueService issueService, Clock clock) {
		this.access = access;
		this.meetings = meetings;
		this.attendees = attendees;
		this.actionItems = actionItems;
		this.mapper = mapper;
		this.projectMembers = projectMembers;
		this.sprints = sprints;
		this.issueService = issueService;
		this.clock = clock;
	}

	record Loaded(Meeting meeting, ProjectAccess access) {
	}

	// ---- read ----

	@Transactional(readOnly = true)
	public List<MeetingSummary> list(UUID userId, UUID projectId, MeetingFilter filter, boolean newestFirst) {
		access.project(userId, projectId);
		Sort sort = newestFirst ? Sort.by(Sort.Order.desc("startsAt"), Sort.Order.asc("id"))
				: Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id"));
		List<Meeting> found = meetings.findAll(filter.toSpecification(projectId), org.springframework.data.domain.PageRequest.of(0, LIST_LIMIT, sort))
			.getContent();
		return mapper.summaries(found, userId);
	}

	@Transactional(readOnly = true)
	public MeetingDetail get(UUID userId, UUID meetingId) {
		Loaded loaded = load(userId, meetingId);
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	// ---- create / update ----

	public MeetingDetail create(UUID userId, UUID projectId, MeetingRequests.Save request) {
		ProjectAccess caller = access.project(userId, projectId).require(ProjectPermission.MANAGE_MEETINGS).requireActive();
		Meeting meeting = new Meeting(projectId, userId);
		apply(meeting, request, userId);
		meeting = meetings.saveAndFlush(meeting);
		replaceAttendees(meeting.getId(), request.attendeeIds());
		return mapper.detail(meeting, caller);
	}

	public MeetingDetail update(UUID userId, UUID meetingId, MeetingRequests.Save request) {
		Loaded loaded = editable(userId, meetingId);
		apply(loaded.meeting(), request, userId);
		meetings.saveAndFlush(loaded.meeting());
		replaceAttendees(meetingId, request.attendeeIds());
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	public MeetingDetail setCancelled(UUID userId, UUID meetingId, boolean cancelled) {
		Loaded loaded = editable(userId, meetingId);
		loaded.meeting().setStatus(cancelled ? MeetingStatus.CANCELLED : MeetingStatus.SCHEDULED);
		meetings.saveAndFlush(loaded.meeting());
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	public void delete(UUID userId, UUID meetingId) {
		editable(userId, meetingId).meeting().markDeleted(clock.instant());
	}

	// ---- minutes and replies ----

	/** Atomic: only the writer whose {@code baseRevision} is still current wins (409 MINUTES_CONFLICT otherwise). */
	public MeetingDetail saveMinutes(UUID userId, UUID meetingId, MeetingRequests.Minutes request) {
		Loaded loaded = notes(userId, meetingId);
		if (meetings.updateMinutes(meetingId, request.minutes(), request.baseRevision()) == 0) {
			throw ApiException.conflict("MINUTES_CONFLICT", "The minutes were changed by someone else");
		}
		Meeting fresh = meetings.findById(meetingId).orElseThrow(MeetingService::notFound);
		return mapper.detail(fresh, loaded.access());
	}

	public MeetingDetail respond(UUID userId, UUID meetingId, AttendeeResponse response) {
		Loaded loaded = load(userId, meetingId);
		loaded.access().requireActive();
		MeetingAttendee mine = attendees.findByMeetingIdAndUserId(meetingId, userId)
			.orElseThrow(() -> ApiException.forbidden("NOT_AN_ATTENDEE", "You are not invited to this meeting"));
		mine.respond(response);
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	// ---- action items ----

	public MeetingDetail addActionItem(UUID userId, UUID meetingId, MeetingRequests.ActionItem request) {
		Loaded loaded = notes(userId, meetingId);
		requireMember(loaded.meeting().getProjectId(), request.assigneeId());
		MeetingActionItem item = new MeetingActionItem(meetingId, userId);
		item.update(request.text().strip(), request.assigneeId(), request.dueDate(), ActionItemStatus.OPEN);
		actionItems.save(item);
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	public MeetingDetail updateActionItem(UUID userId, UUID itemId, MeetingRequests.UpdateActionItem request) {
		MeetingActionItem item = findItem(itemId);
		Loaded loaded = notes(userId, item.getMeetingId());
		requireMember(loaded.meeting().getProjectId(), request.assigneeId());
		item.update(request.text().strip(), request.assigneeId(), request.dueDate(), request.status());
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	public MeetingDetail deleteActionItem(UUID userId, UUID itemId) {
		MeetingActionItem item = findItem(itemId);
		Loaded loaded = notes(userId, item.getMeetingId());
		actionItems.delete(item);
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	/** Creates a TASK in the project from the item (owner and deadline carried over) and remembers it on the item. */
	public MeetingDetail createTask(UUID userId, UUID itemId) {
		MeetingActionItem item = findItem(itemId);
		Loaded loaded = notes(userId, item.getMeetingId());
		if (item.getIssueId() != null) {
			throw ApiException.conflict("ALREADY_CONVERTED", "This action item already became a task");
		}
		IssueResponse issue = issueService.create(userId, loaded.meeting().getProjectId(),
				new IssueRequests.Create(IssueType.TASK, item.getText(), "Từ cuộc họp: " + loaded.meeting().getTitle(), null,
						null, item.getAssigneeId(), null, item.getDueDate(), null, null, null, null, null));
		// Creating an issue bumps the project's counter with a bulk update that clears the persistence context, so
		// the item loaded above is detached by now: load it again before recording the link.
		actionItems.findById(itemId).orElseThrow(() -> ApiException.notFound("ACTION_ITEM_NOT_FOUND", "Action item not found"))
			.convertedTo(issue.id());
		return mapper.detail(loaded.meeting(), loaded.access());
	}

	// ---- helpers ----

	Loaded load(UUID userId, UUID meetingId) {
		Meeting meeting = meetings.findById(meetingId).orElseThrow(MeetingService::notFound);
		try {
			return new Loaded(meeting, access.project(userId, meeting.getProjectId()));
		}
		catch (ApiException ex) {
			throw (ex.getStatus() == HttpStatus.NOT_FOUND) ? notFound() : ex;
		}
	}

	/** For changing details, cancelling or deleting: the organizer or a project manager. */
	private Loaded editable(UUID userId, UUID meetingId) {
		Loaded loaded = notes(userId, meetingId);
		if (!loaded.meeting().getOrganizerId().equals(userId)) {
			loaded.access().require(ProjectPermission.UPDATE_PROJECT);
		}
		return loaded;
	}

	/** For minutes and action items: anyone who may manage meetings. */
	private Loaded notes(UUID userId, UUID meetingId) {
		Loaded loaded = load(userId, meetingId);
		loaded.access().require(ProjectPermission.MANAGE_MEETINGS).requireActive();
		return loaded;
	}

	private MeetingActionItem findItem(UUID itemId) {
		return actionItems.findById(itemId).orElseThrow(() -> ApiException.notFound("ACTION_ITEM_NOT_FOUND", "Action item not found"));
	}

	private void apply(Meeting meeting, MeetingRequests.Save request, UUID callerId) {
		UUID projectId = meeting.getProjectId();
		if (!request.endsAt().isAfter(request.startsAt())) {
			throw ApiException.badRequest("INVALID_TIME", "The meeting must end after it starts");
		}
		if (Duration.between(request.startsAt(), request.endsAt()).compareTo(MAX_LENGTH) > 0) {
			throw ApiException.badRequest("MEETING_TOO_LONG", "A meeting lasts at most 24 hours");
		}
		if (request.sprintId() != null
				&& sprints.findById(request.sprintId()).filter((s) -> s.getProjectId().equals(projectId)).isEmpty()) {
			throw ApiException.badRequest("SPRINT_NOT_IN_PROJECT", "The sprint does not belong to this project");
		}
		UUID organizer = (request.organizerId() != null) ? request.organizerId() : callerId;
		if (!organizer.equals(callerId)) {
			requireMember(projectId, organizer, "ORGANIZER_NOT_MEMBER");
		}
		if (request.attendeeIds() != null) {
			request.attendeeIds().forEach((id) -> requireMember(projectId, id, "ATTENDEE_NOT_MEMBER"));
		}
		meeting.apply(request, organizer, Strings.trimToNull(request.agenda()), Strings.trimToNull(request.description()),
				Strings.trimToNull(request.location()), Strings.trimToNull(request.onlineLink()));
	}

	/** Replaces the invited people; those who stay keep their reply. The organizer is not listed twice. */
	private void replaceAttendees(UUID meetingId, List<UUID> wanted) {
		Meeting meeting = meetings.findById(meetingId).orElseThrow(MeetingService::notFound);
		Set<UUID> ids = (wanted == null) ? Set.of() : new LinkedHashSet<>(wanted);
		ids = ids.stream().filter((id) -> !id.equals(meeting.getOrganizerId())).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<UUID, MeetingAttendee> existing = attendees.findAllByMeetingId(meetingId)
			.stream()
			.collect(Collectors.toMap(MeetingAttendee::getUserId, (a) -> a));
		for (MeetingAttendee a : existing.values()) {
			if (!ids.contains(a.getUserId())) {
				attendees.delete(a);
			}
		}
		for (UUID id : ids) {
			if (!existing.containsKey(id)) {
				attendees.save(new MeetingAttendee(meetingId, id));
			}
		}
	}

	private void requireMember(UUID projectId, UUID userId) {
		requireMember(projectId, userId, "ASSIGNEE_NOT_MEMBER");
	}

	private void requireMember(UUID projectId, UUID userId, String code) {
		if (userId != null && !projectMembers.existsByProjectIdAndUserId(projectId, userId)) {
			throw ApiException.badRequest(code, "That person is not a member of the project");
		}
	}

	private static ApiException notFound() {
		return ApiException.notFound("MEETING_NOT_FOUND", "Meeting not found");
	}

}
