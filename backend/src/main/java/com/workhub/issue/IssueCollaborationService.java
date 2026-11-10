package com.workhub.issue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.issue.IssueLoader.Loaded;
import com.workhub.issue.IssueResponses.ChecklistItemResponse;
import com.workhub.issue.IssueResponses.CommentResponse;
import com.workhub.notification.NotificationService;
import com.workhub.notification.NotificationType;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments and the checklist of an issue. */
@Service
@Transactional
public class IssueCollaborationService {

	private static final int MAX_CHECKLIST_ITEMS = 50;

	private final NotificationService notifications;

	private final ProjectMemberRepository projectMembers;

	private final IssueLoader loader;

	private final IssueCommentRepository comments;

	private final ChecklistItemRepository checklist;

	private final UserRepository users;

	public IssueCollaborationService(IssueLoader loader, IssueCommentRepository comments,
			ChecklistItemRepository checklist, UserRepository users, NotificationService notifications,
			ProjectMemberRepository projectMembers) {
		this.notifications = notifications;
		this.projectMembers = projectMembers;
		this.loader = loader;
		this.comments = comments;
		this.checklist = checklist;
		this.users = users;
	}

	// ---- comments ----

	@Transactional(readOnly = true)
	public List<CommentResponse> listComments(UUID userId, UUID issueId) {
		loader.load(userId, issueId);
		List<IssueComment> list = comments.findAllByIssueIdOrderByCreatedAtAscIdAsc(issueId);
		Map<UUID, User> authors = new HashMap<>();
		users.findAllById(list.stream().map(IssueComment::getAuthorId).collect(Collectors.toSet()))
			.forEach((user) -> authors.put(user.getId(), user));
		return list.stream().map((comment) -> toResponse(comment, authors.get(comment.getAuthorId()))).toList();
	}

	public CommentResponse addComment(UUID userId, UUID issueId, String body, List<UUID> mentions) {
		IssueLoader.Loaded loaded = loader.load(userId, issueId);
		loaded.access().require(ProjectPermission.COMMENT).requireActive();
		Issue issue = loaded.issue();
		Set<UUID> mentioned = new LinkedHashSet<>((mentions == null) ? List.of() : mentions);
		for (UUID id : mentioned) {
			if (!projectMembers.existsByProjectIdAndUserId(issue.getProjectId(), id)) {
				throw ApiException.badRequest("MENTION_NOT_MEMBER", "Only members of the project can be mentioned");
			}
		}
		IssueComment comment = comments.saveAndFlush(new IssueComment(issueId, userId, body.strip()));
		String key = IssueMapper.key(loaded.access().project(), issue);
		String link = "/issues/" + issueId;
		notifications.notifyAll(mentioned, NotificationType.MENTIONED, "Bạn được nhắc đến trong " + key, body.strip(), link,
				issue.getProjectId(), userId, null);
		Set<UUID> watchers = new LinkedHashSet<>(Arrays.asList(issue.getAssigneeId(), issue.getReporterId()));
		watchers.remove(null);
		watchers.removeAll(mentioned);
		notifications.notifyAll(watchers, NotificationType.ISSUE_COMMENTED, "Bình luận mới ở " + key, body.strip(), link,
				issue.getProjectId(), userId, null);
		return toResponse(comment, users.findById(userId).orElseThrow());
	}

	/** Only the author edits a comment. */
	public CommentResponse editComment(UUID userId, UUID commentId, String body) {
		IssueComment comment = comments.findById(commentId).orElseThrow(IssueCollaborationService::commentNotFound);
		loader.load(userId, comment.getIssueId()).access().require(ProjectPermission.COMMENT).requireActive();
		if (!comment.getAuthorId().equals(userId)) {
			throw ApiException.forbidden("FORBIDDEN", "Only the author can edit a comment");
		}
		comment.setBody(body.strip());
		comments.flush();
		return toResponse(comment, users.findById(userId).orElseThrow());
	}

	/** The author, or whoever manages the project, can delete a comment. */
	public void deleteComment(UUID userId, UUID commentId) {
		IssueComment comment = comments.findById(commentId).orElseThrow(IssueCollaborationService::commentNotFound);
		Loaded loaded = loader.load(userId, comment.getIssueId());
		loaded.access().requireActive();
		if (!comment.getAuthorId().equals(userId)) {
			loaded.access().require(ProjectPermission.UPDATE_PROJECT);
		}
		comments.delete(comment);
	}

	// ---- checklist ----

	public ChecklistItemResponse addChecklistItem(UUID userId, UUID issueId, String text) {
		loader.load(userId, issueId).access().require(ProjectPermission.EDIT_ISSUES).requireActive();
		long count = checklist.countByIssueId(issueId);
		if (count >= MAX_CHECKLIST_ITEMS) {
			throw ApiException.conflict("CHECKLIST_FULL", "A checklist holds at most " + MAX_CHECKLIST_ITEMS + " items");
		}
		return toResponse(checklist.saveAndFlush(new ChecklistItem(issueId, text.strip(), (int) count)));
	}

	public ChecklistItemResponse updateChecklistItem(UUID userId, UUID itemId, IssueRequests.ChecklistUpdate request) {
		ChecklistItem item = checklist.findById(itemId).orElseThrow(IssueCollaborationService::itemNotFound);
		loader.load(userId, item.getIssueId()).access().require(ProjectPermission.EDIT_ISSUES).requireActive();
		if (request.text() != null) {
			item.setText(request.text().strip());
		}
		if (request.done() != null) {
			item.setDone(request.done());
		}
		return toResponse(item);
	}

	public void deleteChecklistItem(UUID userId, UUID itemId) {
		ChecklistItem item = checklist.findById(itemId).orElseThrow(IssueCollaborationService::itemNotFound);
		loader.load(userId, item.getIssueId()).access().require(ProjectPermission.EDIT_ISSUES).requireActive();
		checklist.delete(item);
	}

	private static CommentResponse toResponse(IssueComment comment, User author) {
		return new CommentResponse(comment.getId(), IssueMapper.userRef(author), comment.getBody(),
				comment.getCreatedAt(), comment.getUpdatedAt());
	}

	private static ChecklistItemResponse toResponse(ChecklistItem item) {
		return new ChecklistItemResponse(item.getId(), item.getText(), item.isDone());
	}

	private static ApiException commentNotFound() {
		return ApiException.notFound("COMMENT_NOT_FOUND", "Comment not found");
	}

	private static ApiException itemNotFound() {
		return ApiException.notFound("CHECKLIST_ITEM_NOT_FOUND", "Checklist item not found");
	}

}
