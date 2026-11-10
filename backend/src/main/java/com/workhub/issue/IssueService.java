package com.workhub.issue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.board.WorkflowService;
import com.workhub.common.ApiException;
import com.workhub.common.PageResponse;
import com.workhub.common.Strings;
import com.workhub.issue.IssueLoader.Loaded;
import com.workhub.issue.IssueResponses.ActivityResponse;
import com.workhub.issue.IssueResponses.ChecklistItemResponse;
import com.workhub.issue.IssueResponses.IssueDetailResponse;
import com.workhub.issue.IssueResponses.IssueResponse;
import com.workhub.issue.IssueResponses.ProjectStats;
import com.workhub.project.Project;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.alert.AlertSettingsService;
import com.workhub.notification.NotificationService;
import com.workhub.notification.NotificationType;
import com.workhub.project.ProjectRepository;
import com.workhub.project.ProjectRole;
import com.workhub.sprint.SprintRepository;
import com.workhub.sprint.SprintStatus;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and the product backlog. Permissions: EDIT_ISSUES creates and edits, MANAGE_BACKLOG orders and deletes,
 * VIEW reads. Every change is appended to the issue's history.
 */
@Service
@Transactional
public class IssueService {

	/** Distance between neighbouring ranks, so a move normally rewrites a single row. */
	private static final long RANK_GAP = 1024;

	private static final int MAX_PAGE_SIZE = 200;

	private final NotificationService notifications;

	private final AlertSettingsService alertSettings;

	private final AccessService access;

	private final IssueLoader loader;

	private final IssueRepository issues;

	private final IssueMapper mapper;

	private final ProjectRepository projects;

	private final ProjectMemberRepository projectMembers;

	private final ChecklistItemRepository checklist;

	private final IssueActivityRepository activity;

	private final UserRepository users;

	private final Clock clock;

	private final ZoneId zone;

	private final WorkflowService workflow;

	private final SprintRepository sprints;

	private final ApplicationEventPublisher events;

	public IssueService(AccessService access, IssueLoader loader, IssueRepository issues, IssueMapper mapper,
			ProjectRepository projects, ProjectMemberRepository projectMembers, ChecklistItemRepository checklist,
			IssueActivityRepository activity, UserRepository users, Clock clock, ZoneId zone,
			WorkflowService workflow, SprintRepository sprints, ApplicationEventPublisher events,
			NotificationService notifications, AlertSettingsService alertSettings) {
		this.notifications = notifications;
		this.alertSettings = alertSettings;
		this.access = access;
		this.loader = loader;
		this.issues = issues;
		this.mapper = mapper;
		this.projects = projects;
		this.projectMembers = projectMembers;
		this.checklist = checklist;
		this.activity = activity;
		this.users = users;
		this.clock = clock;
		this.zone = zone;
		this.workflow = workflow;
		this.sprints = sprints;
		this.events = events;
	}

	// ---- create / read ----

	public IssueResponse create(UUID userId, UUID projectId, IssueRequests.Create request) {
		ProjectAccess caller = access.project(userId, projectId)
			.require(ProjectPermission.EDIT_ISSUES)
			.requireActive();
		Project project = caller.project();
		checkDates(request.startDate(), request.dueDate());
		checkHierarchy(projectId, null, request.type(), request.parentId());
		if (request.assigneeId() != null) {
			requireProjectMember(projectId, request.assigneeId());
		}
		// Taking the number locks the project row until commit, which also serializes the rank calculation below.
		projects.incrementIssueCounter(projectId);
		int number = projects.readIssueCounter(projectId);
		Issue issue = new Issue(projectId, number, userId, issues.maxRank(projectId) + RANK_GAP);
		issue.setType(request.type());
		issue.setTitle(request.title().strip());
		issue.setDescription(Strings.trimToNull(request.description()));
		issue.setParentId(request.parentId());
		issue.setPriority((request.priority() != null) ? request.priority() : IssuePriority.MEDIUM);
		issue.setAssigneeId(request.assigneeId());
		issue.setStartDate(request.startDate());
		issue.setDueDate(request.dueDate());
		issue.setStoryPoints(request.storyPoints());
		issue.setOriginalEstimateMinutes(request.originalEstimateMinutes());
		issue.setRemainingEstimateMinutes((request.remainingEstimateMinutes() != null)
				? request.remainingEstimateMinutes() : request.originalEstimateMinutes());
		issue.setAcceptanceCriteria(Strings.trimToNull(request.acceptanceCriteria()));
		issue.replaceLabels(normalizeLabels(request.labels()));
		issue = issues.saveAndFlush(issue);
		activity.save(new IssueActivity(issue.getId(), userId, IssueActivity.Kind.CREATED, null, null, null));
		return mapper.toResponse(project, issue, true);
	}

	@Transactional(readOnly = true)
	public PageResponse<IssueResponse> list(UUID userId, UUID projectId, IssueFilter filter, String sort, int page,
			int size) {
		Project project = access.project(userId, projectId).project();
		Sort order = switch (sort == null ? "rank" : sort) {
			case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt", "id");
			case "created" -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
			case "rank" -> Sort.by(Sort.Direction.ASC, "backlogRank", "id");
			default -> throw ApiException.badRequest("INVALID_SORT", "sort must be rank, updated or created");
		};
		Page<Issue> result = issues.findAll(filter.toSpecification(projectId),
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order));
		return PageResponse.of(result, mapper.toResponses(project, result.getContent(), false));
	}

	@Transactional(readOnly = true)
	public IssueDetailResponse get(UUID userId, UUID issueId) {
		Loaded loaded = loader.load(userId, issueId);
		Project project = loaded.access().project();
		Issue issue = loaded.issue();
		List<Issue> children = issues.findAllByParentIdOrderByBacklogRankAscIdAsc(issueId);
		List<ChecklistItemResponse> items = checklist.findAllByIssueIdOrderByPositionAscCreatedAtAsc(issueId)
			.stream()
			.map((item) -> new ChecklistItemResponse(item.getId(), item.getText(), item.isDone()))
			.toList();
		return new IssueDetailResponse(mapper.toResponse(project, issue, true),
				mapper.toResponses(project, children, false), items, loaded.access().permissions());
	}

	@Transactional(readOnly = true)
	public List<String> labels(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		return issues.findLabels(projectId);
	}

	@Transactional(readOnly = true)
	public List<ActivityResponse> history(UUID userId, UUID issueId) {
		loader.load(userId, issueId);
		List<IssueActivity> entries = activity.findByIssueIdOrderByCreatedAtDescIdDesc(issueId, PageRequest.of(0, 200));
		java.util.Map<UUID, User> actors = new java.util.HashMap<>();
		users.findAllById(entries.stream().map(IssueActivity::getActorId).collect(java.util.stream.Collectors.toSet()))
			.forEach((user) -> actors.put(user.getId(), user));
		return entries.stream()
			.map((entry) -> new ActivityResponse(entry.getId(), IssueMapper.userRef(actors.get(entry.getActorId())),
					entry.getKind(), entry.getField(), entry.getOldValue(), entry.getNewValue(), entry.getCreatedAt()))
			.toList();
	}

	/** Counts for the project overview. EPICs are containers and are left out. {@code today} defaults to the server date. */
	@Transactional(readOnly = true)
	public ProjectStats stats(UUID userId, UUID projectId, LocalDate today) {
		access.project(userId, projectId);
		long total = 0, toDo = 0, inProgress = 0, done = 0, points = 0, donePoints = 0;
		for (Object[] row : issues.statusTotals(projectId)) {
			IssueStatus status = (IssueStatus) row[0];
			long count = ((Number) row[1]).longValue();
			long sum = ((Number) row[2]).longValue();
			total += count;
			points += sum;
			switch (status.category()) {
				case TO_DO -> toDo += count;
				case IN_PROGRESS -> inProgress += count;
				case DONE -> {
					done += count;
					donePoints += sum;
				}
			}
		}
		LocalDate day = (today != null) ? today : LocalDate.now(clock.withZone(zone));
		return new ProjectStats(total, toDo, inProgress, done, issues.countOverdue(projectId, day), points, donePoints);
	}

	// ---- update ----

	public IssueResponse update(UUID userId, UUID issueId, IssueRequests.Update request) {
		Loaded loaded = editable(userId, issueId);
		Issue issue = loaded.issue();
		checkDates(request.startDate(), request.dueDate());
		checkHierarchy(issue.getProjectId(), issue, request.type(), request.parentId());
		if (request.type() == IssueType.EPIC && issue.getSprintId() != null) {
			throw ApiException.conflict("EPIC_IN_SPRINT", "Remove the issue from its sprint before making it an epic");
		}
		Project project = loaded.access().project();
		boolean scopeChange = !Objects.equals(issue.getStoryPoints(), request.storyPoints());

		History history = new History(issue.getId(), userId);
		history.text("title", issue.getTitle(), request.title().strip());
		history.text("description", issue.getDescription(), Strings.trimToNull(request.description()));
		history.value("type", issue.getType(), request.type());
		history.value("priority", issue.getPriority(), request.priority());
		history.value("refinement", issue.getRefinement(), request.refinement());
		history.value("parent", parentLabel(project, issue.getParentId()), parentLabel(project, request.parentId()));
		history.value("startDate", issue.getStartDate(), request.startDate());
		history.value("dueDate", issue.getDueDate(), request.dueDate());
		history.value("storyPoints", issue.getStoryPoints(), request.storyPoints());
		history.value("originalEstimate", issue.getOriginalEstimateMinutes(), request.originalEstimateMinutes());
		history.value("remainingEstimate", issue.getRemainingEstimateMinutes(), request.remainingEstimateMinutes());
		history.text("acceptanceCriteria", issue.getAcceptanceCriteria(), Strings.trimToNull(request.acceptanceCriteria()));
		Set<String> labels = normalizeLabels(request.labels());
		history.value("labels", String.join(", ", new java.util.TreeSet<>(issue.getLabels())),
				String.join(", ", new java.util.TreeSet<>(labels)));

		issue.setTitle(request.title().strip());
		issue.setDescription(Strings.trimToNull(request.description()));
		issue.setType(request.type());
		issue.setPriority(request.priority());
		issue.setRefinement(request.refinement());
		issue.setParentId(request.parentId());
		issue.setStartDate(request.startDate());
		issue.setDueDate(request.dueDate());
		issue.setStoryPoints(request.storyPoints());
		issue.setOriginalEstimateMinutes(request.originalEstimateMinutes());
		issue.setRemainingEstimateMinutes(request.remainingEstimateMinutes());
		issue.setAcceptanceCriteria(Strings.trimToNull(request.acceptanceCriteria()));
		issue.replaceLabels(labels);
		history.saveTo(activity);
		if (scopeChange) {
			announce(issue);
		}
		return mapper.toResponse(project, issue, true);
	}

	public IssueResponse changeStatus(UUID userId, UUID issueId, IssueStatus status) {
		Loaded loaded = editable(userId, issueId);
		Issue issue = loaded.issue();
		if (issue.getStatus() != status) {
			workflow.requireEnabled(issue.getProjectId(), status);
		}
		IssueStatus before = issue.getStatus();
		History history = new History(issue.getId(), userId);
		history.value("status", issue.getStatus(), status);
		issue.changeStatus(status, clock.instant());
		history.saveTo(activity);
		announce(issue);
		if (before != status) {
			String key = IssueMapper.key(loaded.access().project(), issue);
			java.util.Set<UUID> audience = new java.util.LinkedHashSet<>();
			audience.add(issue.getAssigneeId());
			audience.add(issue.getReporterId());
			audience.remove(null);
			notifications.notifyAll(audience, NotificationType.ISSUE_STATUS_CHANGED,
					key + " chuyển sang " + statusName(issue.getProjectId(), status), issue.getTitle(),
					"/issues/" + issue.getId(), issue.getProjectId(), userId, null);
		}
		return mapper.toResponse(loaded.access().project(), issue, true);
	}

	/** Flags an issue as blocked (with a reason) or clears the flag. */
	public IssueResponse block(UUID userId, UUID issueId, boolean blocked, String reason) {
		Loaded loaded = editable(userId, issueId);
		Issue issue = loaded.issue();
		String cleanReason = blocked ? Strings.trimToNull(reason) : null;
		History history = new History(issue.getId(), userId);
		history.value("blocked", issue.isBlocked(), blocked);
		history.value("blockedReason", issue.getBlockedReason(), cleanReason);
		boolean wasBlocked = issue.isBlocked();
		issue.setBlocked(blocked, cleanReason);
		history.saveTo(activity);
		if (blocked && !wasBlocked && alertSettings.valuesOf(issue.getProjectId()).blockedEnabled()) {
			java.util.Set<UUID> audience = new java.util.LinkedHashSet<>(projectMembers.findUserIdsByRole(issue.getProjectId(),
					java.util.List.of(ProjectRole.PROJECT_MANAGER, ProjectRole.SCRUM_MASTER, ProjectRole.PRODUCT_OWNER)));
			audience.add(issue.getAssigneeId());
			audience.remove(null);
			notifications.notifyAll(audience, NotificationType.TASK_BLOCKED,
					IssueMapper.key(loaded.access().project(), issue) + " bị chặn", (cleanReason != null) ? cleanReason : issue.getTitle(),
					"/issues/" + issue.getId(), issue.getProjectId(), userId, null);
		}
		return mapper.toResponse(loaded.access().project(), issue, true);
	}

	private String statusName(UUID projectId, IssueStatus status) {
		return workflow.columns(projectId)
			.stream()
			.filter((entry) -> entry.status() == status)
			.map(com.workhub.board.WorkflowEntry::name)
			.findFirst()
			.orElse(status.name());
	}

	/** Lets the sprint module refresh its numbers when a sprint issue's status or points change. */
	private void announce(Issue issue) {
		if (issue.getSprintId() != null) {
			events.publishEvent(new IssueChangedEvent(issue.getId(), issue.getSprintId()));
		}
	}

	public IssueResponse assign(UUID userId, UUID issueId, UUID assigneeId) {
		Loaded loaded = editable(userId, issueId);
		Issue issue = loaded.issue();
		if (assigneeId != null) {
			requireProjectMember(issue.getProjectId(), assigneeId);
		}
		History history = new History(issue.getId(), userId);
		history.value("assignee", userName(issue.getAssigneeId()), userName(assigneeId));
		UUID previous = issue.getAssigneeId();
		issue.setAssigneeId(assigneeId);
		history.saveTo(activity);
		if (assigneeId != null && !assigneeId.equals(previous)) {
			notifications.notify(assigneeId, NotificationType.ISSUE_ASSIGNED,
					"Bạn được giao " + IssueMapper.key(loaded.access().project(), issue) + ": " + issue.getTitle(), null,
					"/issues/" + issue.getId(), issue.getProjectId(), userId, null);
		}
		return mapper.toResponse(loaded.access().project(), issue, true);
	}

	// ---- backlog order ----

	/** Moves the issue right after {@code afterIssueId}, or to the top when that is null. */
	public IssueResponse rank(UUID userId, UUID issueId, UUID afterIssueId) {
		Loaded loaded = loader.load(userId, issueId);
		loaded.access().require(ProjectPermission.MANAGE_BACKLOG).requireActive();
		Issue issue = loaded.issue();
		if (!issueId.equals(afterIssueId)) {
			Issue after = (afterIssueId == null) ? null
					: issues.findByIdAndProjectId(afterIssueId, issue.getProjectId())
						.orElseThrow(() -> ApiException.badRequest("INVALID_RANK_TARGET", "Target issue is not in this project"));
			issue.setBacklogRank(rankAfter(issue, after));
		}
		return mapper.toResponse(loaded.access().project(), issue, false);
	}

	private long rankAfter(Issue moving, Issue after) {
		UUID projectId = moving.getProjectId();
		for (int attempt = 0; attempt < 2; attempt++) {
			long previous = (after == null) ? 0 : after.getBacklogRank();
			Optional<Long> next = (after == null) ? issues.minRankExcluding(projectId, moving.getId())
					: issues.nextRankAfter(projectId, moving.getId(), previous);
			if (next.isEmpty()) {
				return previous + RANK_GAP;
			}
			long gap = next.get() - previous;
			if (gap >= 2) {
				return previous + gap / 2;
			}
			rebalance(projectId);
		}
		throw new IllegalStateException("Backlog ranks could not be rebalanced");
	}

	/** Spreads all ranks of the project out again, keeping their order. */
	private void rebalance(UUID projectId) {
		long rank = RANK_GAP;
		for (Issue issue : issues.findAllByProjectIdOrderByBacklogRankAscIdAsc(projectId)) {
			issue.setBacklogRank(rank);
			rank += RANK_GAP;
		}
		issues.flush();
	}

	// ---- delete ----

	/** Soft delete (history stays). An issue with children must have them moved or deleted first. */
	public void delete(UUID userId, UUID issueId) {
		Loaded loaded = loader.load(userId, issueId);
		loaded.access().require(ProjectPermission.MANAGE_BACKLOG).requireActive();
		if (issues.existsByParentId(issueId)) {
			throw ApiException.conflict("HAS_CHILDREN", "Move or delete the child issues first");
		}
		UUID sprintId = loaded.issue().getSprintId();
		if (sprintId != null && sprints.existsByIdAndStatusIn(sprintId, List.of(SprintStatus.FUTURE, SprintStatus.ACTIVE))) {
			throw ApiException.conflict("IN_SPRINT", "Remove the issue from its sprint first");
		}
		loaded.issue().markDeleted(clock.instant());
	}

	// ---- helpers ----

	private Loaded editable(UUID userId, UUID issueId) {
		Loaded loaded = loader.load(userId, issueId);
		loaded.access().require(ProjectPermission.EDIT_ISSUES).requireActive();
		return loaded;
	}

	private void requireProjectMember(UUID projectId, UUID userId) {
		if (!projectMembers.existsByProjectIdAndUserId(projectId, userId)) {
			throw ApiException.badRequest("ASSIGNEE_NOT_MEMBER", "Assignee must be a member of the project");
		}
	}

	private static void checkDates(LocalDate start, LocalDate due) {
		if (start != null && due != null && due.isBefore(start)) {
			throw ApiException.badRequest("INVALID_DATES", "Due date must not be before start date");
		}
	}

	/**
	 * The parent must be in the same project and of an allowed type, SUB_TASKs must have one, and when an existing issue
	 * changes type its children must still be allowed under the new type.
	 */
	private void checkHierarchy(UUID projectId, Issue existing, IssueType type, UUID parentId) {
		if (parentId == null) {
			if (type.requiresParent()) {
				throw ApiException.badRequest("PARENT_REQUIRED", type + " needs a parent issue");
			}
		}
		else {
			Issue parent = issues.findByIdAndProjectId(parentId, projectId)
				.orElseThrow(() -> ApiException.badRequest("INVALID_PARENT", "Parent issue not found in this project"));
			if (!type.allowedParents().contains(parent.getType())) {
				throw ApiException.badRequest("INVALID_PARENT", type + " cannot be a child of " + parent.getType());
			}
		}
		if (existing != null) {
			for (Issue child : issues.findAllByParentIdOrderByBacklogRankAscIdAsc(existing.getId())) {
				if (!child.getType().allowedParents().contains(type)) {
					throw ApiException.conflict("CHILD_TYPE_CONFLICT",
							"Existing " + child.getType() + " children cannot sit under " + type);
				}
			}
		}
	}

	private static Set<String> normalizeLabels(List<String> labels) {
		Set<String> normalized = new LinkedHashSet<>();
		if (labels != null) {
			for (String label : labels) {
				String value = Strings.trimToNull(label);
				if (value != null) {
					normalized.add(value.toLowerCase(Locale.ROOT));
				}
			}
		}
		return normalized;
	}

	private String parentLabel(Project project, UUID parentId) {
		return (parentId == null) ? null
				: issues.findById(parentId).map((parent) -> IssueMapper.key(project, parent)).orElse(null);
	}

	private String userName(UUID userId) {
		return (userId == null) ? null : users.findById(userId).map(User::getFullName).orElse(null);
	}

	/** Collects the differences between old and new values; unchanged fields produce no entry. */
	private static final class History {

		private final List<IssueActivity> entries = new ArrayList<>();

		private final UUID issueId;

		private final UUID actorId;

		History(UUID issueId, UUID actorId) {
			this.issueId = issueId;
			this.actorId = actorId;
		}

		void value(String field, Object before, Object after) {
			if (!Objects.equals(before, after)) {
				entries.add(new IssueActivity(issueId, actorId, IssueActivity.Kind.CHANGED, field, text(before), text(after)));
			}
		}

		/** For long free text only the fact of the change is kept. */
		void text(String field, String before, String after) {
			if (!Objects.equals(before, after)) {
				boolean longText = field.equals("description") || field.equals("acceptanceCriteria");
				entries.add(new IssueActivity(issueId, actorId, IssueActivity.Kind.CHANGED, field,
						longText ? null : text(before), longText ? null : text(after)));
			}
		}

		void saveTo(IssueActivityRepository repository) {
			repository.saveAll(entries);
		}

		private static String text(Object value) {
			if (value == null) {
				return null;
			}
			String text = value.toString();
			return (text.length() > 500) ? text.substring(0, 500) : text;
		}

	}

}
