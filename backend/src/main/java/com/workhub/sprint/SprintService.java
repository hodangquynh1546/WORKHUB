package com.workhub.sprint;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.workhub.issue.Issue;
import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueStatus;
import com.workhub.issue.IssueType;
import com.workhub.project.Methodology;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.sprint.SprintResponses.SnapshotResponse;
import com.workhub.sprint.SprintResponses.SprintDetail;
import com.workhub.sprint.SprintResponses.SprintReport;
import com.workhub.sprint.SprintResponses.SprintResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint planning and lifecycle. Every scope change is written to {@code sprint_issues} (and, for an active sprint,
 * to today's snapshot) so reports can be reconstructed later. Needs MANAGE_SPRINTS on the project to change anything.
 */
@Service
@Transactional
public class SprintService {

	private final AccessService access;

	private final SprintRepository sprints;

	private final SprintIssueRepository members;

	private final IssueRepository issues;

	private final IssueMapper issueMapper;

	private final SprintMapper sprintMapper;

	private final SprintStats stats;

	private final SprintSnapshotService snapshots;

	private final ProjectMemberRepository projectMembers;

	private final Clock clock;

	private final ZoneId zone;

	public SprintService(AccessService access, SprintRepository sprints, SprintIssueRepository members,
			IssueRepository issues, IssueMapper issueMapper, SprintMapper sprintMapper, SprintStats stats,
			SprintSnapshotService snapshots, ProjectMemberRepository projectMembers, Clock clock, ZoneId zone) {
		this.access = access;
		this.sprints = sprints;
		this.members = members;
		this.issues = issues;
		this.issueMapper = issueMapper;
		this.sprintMapper = sprintMapper;
		this.stats = stats;
		this.snapshots = snapshots;
		this.projectMembers = projectMembers;
		this.clock = clock;
		this.zone = zone;
	}

	private record Loaded(Sprint sprint, ProjectAccess access) {
	}

	// ---- create / read / update ----

	public SprintResponse create(UUID userId, UUID projectId, SprintRequests.Save request) {
		ProjectAccess caller = access.project(userId, projectId)
			.require(ProjectPermission.MANAGE_SPRINTS)
			.requireActive();
		requireScrum(caller);
		validate(request, projectId);
		Sprint sprint = new Sprint(projectId, userId);
		apply(sprint, request);
		return sprintMapper.toResponse(sprints.saveAndFlush(sprint));
	}

	@Transactional(readOnly = true)
	public List<SprintResponse> list(UUID userId, UUID projectId, List<SprintStatus> statuses) {
		access.project(userId, projectId);
		List<Sprint> list = (statuses == null || statuses.isEmpty())
				? sprints.findAllByProjectIdOrderByStartDateDescCreatedAtDesc(projectId)
				: sprints.findAllByProjectIdAndStatusInOrderByStartDateDescCreatedAtDesc(projectId, statuses);
		return sprintMapper.toResponses(list);
	}

	@Transactional(readOnly = true)
	public SprintDetail get(UUID userId, UUID sprintId, LocalDate today) {
		Loaded loaded = load(userId, sprintId);
		Sprint sprint = loaded.sprint();
		List<Issue> inSprint = issues.findAllBySprintIdOrderByBacklogRankAscIdAsc(sprintId);
		return new SprintDetail(sprintMapper.toResponse(sprint), stats.dashboard(sprint, day(today)),
				issueMapper.toResponses(loaded.access().project(), inSprint, false), loaded.access().permissions());
	}

	/** A future sprint can change entirely; an active one cannot move its start date; closed ones are frozen. */
	public SprintResponse update(UUID userId, UUID sprintId, SprintRequests.Save request) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		if (!sprint.getStatus().isOpen()) {
			throw ApiException.conflict("SPRINT_CLOSED", "A " + sprint.getStatus() + " sprint cannot be changed");
		}
		if (sprint.getStatus() == SprintStatus.ACTIVE && !sprint.getStartDate().equals(request.startDate())) {
			throw ApiException.badRequest("START_DATE_FIXED", "The start date of a running sprint cannot change");
		}
		validate(request, sprint.getProjectId());
		apply(sprint, request);
		return sprintMapper.toResponse(sprint);
	}

	// ---- scope ----

	public SprintDetail addIssues(UUID userId, UUID sprintId, SprintRequests.AddIssues request) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		requireOpen(sprint);
		Instant now = clock.instant();

		Set<UUID> requestedIds = new LinkedHashSet<>(request.issueIds());
		Map<UUID, Issue> chosen = new LinkedHashMap<>();
		for (Issue issue : issues.findAllById(requestedIds)) {
			if (!issue.getProjectId().equals(sprint.getProjectId())) {
				throw ApiException.badRequest("INVALID_ISSUE", "Issue " + issue.getId() + " is not in this project");
			}
			if (issue.getType() == IssueType.EPIC) {
				throw ApiException.badRequest("EPIC_NOT_ALLOWED", "Epics are containers and cannot be planned into a sprint");
			}
			if (issue.getStatus() == IssueStatus.DONE) {
				throw ApiException.conflict("ISSUE_DONE", issue.getTitle() + " is already done");
			}
			chosen.put(issue.getId(), issue);
		}
		if (chosen.size() != requestedIds.size()) {
			throw ApiException.badRequest("INVALID_ISSUE", "Some issues do not exist");
		}
		if (request.includeChildren()) {
			for (Issue child : descendants(chosen.keySet())) {
				if (child.getType() != IssueType.EPIC && child.getStatus() != IssueStatus.DONE) {
					chosen.putIfAbsent(child.getId(), child);
				}
			}
		}

		Set<Sprint> touched = new LinkedHashSet<>(List.of(sprint));
		for (Issue issue : chosen.values()) {
			if (sprint.getId().equals(issue.getSprintId())) {
				continue;
			}
			if (issue.getSprintId() != null) {
				sprints.findById(issue.getSprintId()).filter((previous) -> previous.getStatus().isOpen())
					.ifPresent((previous) -> {
						leave(previous, issue, userId, now, RemovalReason.MOVED);
						touched.add(previous);
					});
			}
			join(sprint, issue, userId, now);
		}
		touched.forEach(snapshots::capture);
		return get(userId, sprintId, null);
	}

	public SprintDetail removeIssue(UUID userId, UUID sprintId, UUID issueId, boolean includeChildren) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		requireOpen(sprint);
		Issue issue = issues.findByIdAndProjectId(issueId, sprint.getProjectId())
			.filter((candidate) -> sprintId.equals(candidate.getSprintId()))
			.orElseThrow(() -> ApiException.notFound("ISSUE_NOT_IN_SPRINT", "Issue is not in this sprint"));
		Instant now = clock.instant();
		leave(sprint, issue, userId, now, RemovalReason.REMOVED);
		if (includeChildren) {
			for (Issue child : descendants(Set.of(issueId))) {
				if (sprintId.equals(child.getSprintId())) {
					leave(sprint, child, userId, now, RemovalReason.REMOVED);
				}
			}
		}
		snapshots.capture(sprint);
		return get(userId, sprintId, null);
	}

	// ---- lifecycle ----

	/** One active sprint per project; needs at least one issue. Freezes the commitment. */
	public SprintDetail start(UUID userId, UUID sprintId) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		if (sprint.getStatus() != SprintStatus.FUTURE) {
			throw ApiException.conflict("SPRINT_NOT_FUTURE", "Only a future sprint can be started");
		}
		sprints.lockProject(sprint.getProjectId());
		if (sprints.findFirstByProjectIdAndStatus(sprint.getProjectId(), SprintStatus.ACTIVE).isPresent()) {
			throw ApiException.conflict("SPRINT_ALREADY_ACTIVE", "Complete the running sprint first");
		}
		SprintCounts counts = stats.counts(sprintId);
		if (counts.issues() == 0) {
			throw ApiException.conflict("SPRINT_EMPTY", "Add issues to the sprint before starting it");
		}
		sprint.start(clock.instant(), counts.issues(), counts.scopePoints());
		snapshots.capture(sprint);
		return get(userId, sprintId, null);
	}

	/**
	 * Closes the sprint. Only issues in status DONE count as done; the rest are carried to the backlog or the next
	 * sprint, as the caller decides. The final snapshot is taken before anything is moved.
	 */
	public SprintDetail complete(UUID userId, UUID sprintId, SprintRequests.Complete request) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		if (sprint.getStatus() != SprintStatus.ACTIVE) {
			throw ApiException.conflict("SPRINT_NOT_ACTIVE", "Only a running sprint can be completed");
		}
		Sprint next = null;
		if (request.unfinishedTo() == SprintRequests.UnfinishedTo.NEXT_SPRINT) {
			next = request.nextSprintId() == null ? null : sprints.findById(request.nextSprintId()).orElse(null);
			if (next == null || !next.getProjectId().equals(sprint.getProjectId())
					|| next.getStatus() != SprintStatus.FUTURE) {
				throw ApiException.badRequest("INVALID_NEXT_SPRINT", "Choose a future sprint of this project");
			}
		}
		Instant now = clock.instant();
		snapshots.capture(sprint);

		Map<UUID, SprintIssue> open = members.findAllBySprintIdAndRemovedAtIsNull(sprintId)
			.stream()
			.collect(Collectors.toMap(SprintIssue::getIssueId, (row) -> row));
		int doneIssues = 0, donePoints = 0;
		for (Issue issue : issues.findAllBySprintIdOrderByBacklogRankAscIdAsc(sprintId)) {
			SprintIssue row = open.get(issue.getId());
			if (row == null) {
				continue;
			}
			row.recordOutcome(issue.getStatus(), issue.getStoryPoints());
			if (issue.getStatus() == IssueStatus.DONE) {
				doneIssues++;
				donePoints += points(issue);
				continue;
			}
			row.close(now, userId, RemovalReason.CARRIED_OVER, issue.getStoryPoints());
			if (next != null) {
				members.save(new SprintIssue(next.getId(), issue.getId(), now, userId, issue.getStoryPoints(), false));
				issue.moveToSprint(next.getId());
			}
			else {
				issue.moveToSprint(null);
			}
		}
		sprint.complete(now, doneIssues, donePoints, request.goalOutcome(), Strings.trimToNull(request.goalNote()));
		return get(userId, sprintId, null);
	}

	/** Cancelling puts every issue back in the backlog. */
	public SprintResponse cancel(UUID userId, UUID sprintId) {
		Loaded loaded = manage(userId, sprintId);
		Sprint sprint = loaded.sprint();
		requireOpen(sprint);
		Instant now = clock.instant();
		for (Issue issue : issues.findAllBySprintIdOrderByBacklogRankAscIdAsc(sprintId)) {
			leave(sprint, issue, userId, now, RemovalReason.CANCELLED);
		}
		sprint.cancel(now);
		return sprintMapper.toResponse(sprint);
	}

	// ---- reports ----

	@Transactional(readOnly = true)
	public SprintReport report(UUID userId, UUID sprintId) {
		Loaded loaded = load(userId, sprintId);
		Sprint sprint = loaded.sprint();
		if (sprint.getStatus() != SprintStatus.COMPLETED) {
			throw ApiException.conflict("SPRINT_NOT_COMPLETED", "The report is available once the sprint is completed");
		}
		List<SprintIssue> rows = members.findAllBySprintIdOrderByAddedAtAscIdAsc(sprintId);
		List<UUID> done = new ArrayList<>(), unfinished = new ArrayList<>(), removed = new ArrayList<>();
		for (SprintIssue row : rows) {
			if (row.getRemovalReason() == null) {
				done.add(row.getIssueId());
			}
			else if (row.getRemovalReason() == RemovalReason.CARRIED_OVER) {
				unfinished.add(row.getIssueId());
			}
			else {
				removed.add(row.getIssueId());
			}
		}
		com.workhub.project.Project project = loaded.access().project();
		return new SprintReport(sprintMapper.toResponse(sprint), stats.dashboard(sprint, day(null)),
				issuesById(project, done), issuesById(project, unfinished), issuesById(project, removed),
				snapshots.history(sprintId).stream().map(SprintMapper::toResponse).toList());
	}

	@Transactional(readOnly = true)
	public List<SnapshotResponse> history(UUID userId, UUID sprintId) {
		load(userId, sprintId);
		return snapshots.history(sprintId).stream().map(SprintMapper::toResponse).toList();
	}

	// ---- helpers ----

	private void join(Sprint sprint, Issue issue, UUID userId, Instant now) {
		members.save(new SprintIssue(sprint.getId(), issue.getId(), now, userId, issue.getStoryPoints(),
				sprint.getStatus() == SprintStatus.ACTIVE));
		issue.moveToSprint(sprint.getId());
	}

	private void leave(Sprint sprint, Issue issue, UUID userId, Instant now, RemovalReason reason) {
		members.findFirstBySprintIdAndIssueIdAndRemovedAtIsNull(sprint.getId(), issue.getId())
			.ifPresent((row) -> row.close(now, userId, reason, issue.getStoryPoints()));
		issue.moveToSprint(null);
	}

	/** All descendants of the given issues, three levels deep at most. */
	private List<Issue> descendants(Set<UUID> rootIds) {
		List<Issue> found = new ArrayList<>();
		Set<UUID> frontier = new LinkedHashSet<>(rootIds);
		for (int level = 0; level < 3 && !frontier.isEmpty(); level++) {
			List<Issue> children = issues.findAllByParentIdIn(frontier);
			found.addAll(children);
			frontier = children.stream().map(Issue::getId).collect(Collectors.toCollection(LinkedHashSet::new));
		}
		return found;
	}

	private List<com.workhub.issue.IssueResponses.IssueResponse> issuesById(com.workhub.project.Project project,
			List<UUID> ids) {
		Map<UUID, Issue> byId = new HashMap<>();
		issues.findAllById(ids).forEach((issue) -> byId.put(issue.getId(), issue));
		List<Issue> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
		return issueMapper.toResponses(project, ordered, false);
	}

	private Loaded load(UUID userId, UUID sprintId) {
		Sprint sprint = sprints.findById(sprintId).orElseThrow(SprintService::notFound);
		try {
			return new Loaded(sprint, access.project(userId, sprint.getProjectId()));
		}
		catch (ApiException ex) {
			throw (ex.getStatus() == HttpStatus.NOT_FOUND) ? notFound() : ex;
		}
	}

	private Loaded manage(UUID userId, UUID sprintId) {
		Loaded loaded = load(userId, sprintId);
		loaded.access().require(ProjectPermission.MANAGE_SPRINTS).requireActive();
		return loaded;
	}

	private static void requireOpen(Sprint sprint) {
		if (!sprint.getStatus().isOpen()) {
			throw ApiException.conflict("SPRINT_CLOSED", "A " + sprint.getStatus() + " sprint cannot be changed");
		}
	}

	private static void requireScrum(ProjectAccess caller) {
		if (caller.project().getMethodology() != Methodology.SCRUM) {
			throw ApiException.badRequest("SPRINTS_NOT_SUPPORTED", "Only Scrum projects have sprints");
		}
	}

	private void validate(SprintRequests.Save request, UUID projectId) {
		if (request.endDate().isBefore(request.startDate())) {
			throw ApiException.badRequest("INVALID_DATES", "End date must not be before start date");
		}
		if (request.endDate().isAfter(request.startDate().plusMonths(1))) {
			throw ApiException.badRequest("SPRINT_TOO_LONG", "A sprint lasts at most one month");
		}
		if (request.facilitatorId() != null && !projectMembers.existsByProjectIdAndUserId(projectId, request.facilitatorId())) {
			throw ApiException.badRequest("FACILITATOR_NOT_MEMBER", "The facilitator must be a member of the project");
		}
	}

	private static void apply(Sprint sprint, SprintRequests.Save request) {
		sprint.setDetails(request.name().strip(), Strings.trimToNull(request.goal()), request.startDate(),
				request.endDate(), request.facilitatorId(), request.capacityPoints());
	}

	private LocalDate day(LocalDate requested) {
		return (requested != null) ? requested : LocalDate.now(clock.withZone(zone));
	}

	private static int points(Issue issue) {
		return (issue.getStoryPoints() != null) ? issue.getStoryPoints() : 0;
	}

	private static ApiException notFound() {
		return ApiException.notFound("SPRINT_NOT_FOUND", "Sprint not found");
	}

}
