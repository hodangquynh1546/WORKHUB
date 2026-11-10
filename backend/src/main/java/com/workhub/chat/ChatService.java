package com.workhub.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.AccessService;

import com.workhub.chat.ChatResponses.ConversationSummary;
import com.workhub.chat.ChatResponses.MessagePage;
import com.workhub.chat.ChatResponses.MessagePreview;
import com.workhub.chat.ChatResponses.MessageResponse;
import com.workhub.chat.ChatResponses.SearchHit;
import com.workhub.common.ApiException;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.notification.NotificationService;
import com.workhub.notification.NotificationType;
import com.workhub.project.Project;
import com.workhub.project.ProjectMemberRepository;
import com.workhub.project.ProjectRepository;
import com.workhub.realtime.RealtimeHub;
import com.workhub.team.Team;
import com.workhub.team.TeamMemberRepository;
import com.workhub.team.TeamRepository;
import com.workhub.workspace.WorkspaceMemberRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat. Who may read a conversation: PROJECT channel = anyone who can see the project, TEAM channel = team members,
 * DIRECT and GROUP = the listed people (who must share a workspace). Everything else is a 404 so ids cannot be
 * probed. Events go out over the WebSocket only after the transaction commits.
 *
 * <p>Unread counts: a conversation row per user in {@code conversation_reads} says how far they read; a user
 * without a row has nothing unread, and project/team channels create that row the first time the user lists them.
 */
@Service
@Transactional
public class ChatService {

	private static final int MAX_PAGE = 100;

	private static final int MAX_GROUP = 50;

	private final ConversationRepository conversations;

	private final ConversationMemberRepository members;

	private final ChatMessageRepository messages;

	private final MessageReactionRepository reactions;

	private final MessageMentionRepository mentions;

	private final ConversationReadRepository reads;

	private final ChatMapper mapper;

	private final AccessService access;

	private final ProjectMemberRepository projectMembers;

	private final TeamMemberRepository teamMembers;

	private final WorkspaceMemberRepository workspaceMembers;

	private final ProjectRepository projects;

	private final TeamRepository teams;

	private final NotificationService notifications;

	private final RealtimeHub hub;

	private final Clock clock;

	ChatService(ConversationRepository conversations, ConversationMemberRepository members, ChatMessageRepository messages, MessageReactionRepository reactions, MessageMentionRepository mentions,
			ConversationReadRepository reads, ChatMapper mapper, AccessService access, ProjectMemberRepository projectMembers,
			TeamMemberRepository teamMembers, WorkspaceMemberRepository workspaceMembers, ProjectRepository projects,
			TeamRepository teams, NotificationService notifications, RealtimeHub hub, Clock clock) {
		this.conversations = conversations;
		this.members = members;
		this.messages = messages;
		this.reactions = reactions;
		this.mentions = mentions;
		this.reads = reads;
		this.mapper = mapper;
		this.access = access;
		this.projectMembers = projectMembers;
		this.teamMembers = teamMembers;
		this.workspaceMembers = workspaceMembers;
		this.projects = projects;
		this.teams = teams;
		this.notifications = notifications;
		this.hub = hub;
		this.clock = clock;
	}

	// ---- conversations ----

	/** Everything the user can chat in, newest activity first; project and team channels are created on first sight. */
	public List<ConversationSummary> list(UUID userId) {
		Directory directory = directory(userId);
		List<UUID> ids = directory.conversations().stream().map(Conversation::getId).toList();
		Map<UUID, Long> unread = unread(userId, ids);
		Map<UUID, ChatMessage> latest = new HashMap<>();
		if (!ids.isEmpty()) {
			messages.latestIn(ids).forEach((m) -> latest.putIfAbsent(m.getConversationId(), m));
		}
		Set<UUID> people = new LinkedHashSet<>();
		latest.values().forEach((m) -> people.add(m.getSenderId()));
		Map<UUID, Set<UUID>> directMembers = new HashMap<>();
		Map<UUID, Integer> counts = new HashMap<>();
		for (Conversation c : directory.conversations()) {
			if (c.getKind() == ConversationKind.DIRECT || c.getKind() == ConversationKind.GROUP) {
				Set<UUID> ids2 = new LinkedHashSet<>();
				members.findAllByConversationId(c.getId()).forEach((m) -> ids2.add(m.getUserId()));
				directMembers.put(c.getId(), ids2);
				counts.put(c.getId(), ids2.size());
				if (c.getKind() == ConversationKind.DIRECT) {
					people.addAll(ids2);
				}
			}
		}
		Map<UUID, UserRef> refs = mapper.userRefs(people);
		List<ConversationSummary> out = new ArrayList<>();
		for (Conversation c : directory.conversations()) {
			UserRef other = null;
			String title = directory.titles().get(c.getId());
			if (c.getKind() == ConversationKind.DIRECT) {
				UUID otherId = directMembers.getOrDefault(c.getId(), Set.of()).stream().filter((id) -> !id.equals(userId)).findFirst().orElse(null);
				other = refs.get(otherId);
				title = (other == null) ? "Cuộc trò chuyện" : other.fullName();
			}
			else if (c.getKind() == ConversationKind.GROUP) {
				title = c.getTitle();
			}
			ChatMessage last = latest.get(c.getId());
			MessagePreview preview = (last == null) ? null : new MessagePreview(last.getSenderId(),
					(refs.get(last.getSenderId()) == null) ? "" : refs.get(last.getSenderId()).fullName(),
					(last.getDeletedAt() != null) ? "Tin nhắn đã bị xóa" : ChatMapper.snippet(last.getBody()), last.getCreatedAt());
			out.add(new ConversationSummary(c.getId(), c.getKind(), title, c.getProjectId(), c.getTeamId(), other,
					counts.getOrDefault(c.getId(), 0), preview, unread.getOrDefault(c.getId(), 0L), c.getLastMessageAt()));
		}
		out.sort(Comparator.comparing((ConversationSummary s) -> s.lastMessageAt() == null ? Instant.EPOCH : s.lastMessageAt()).reversed()
			.thenComparing(ConversationSummary::title, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	public ConversationSummary openProject(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		Conversation c = ensureProject(projects.findById(projectId).orElseThrow());
		ensureRead(c.getId(), userId);
		return summary(userId, c);
	}

	public ConversationSummary openDirect(UUID userId, UUID workspaceId, UUID otherId) {
		if (userId.equals(otherId)) {
			throw ApiException.badRequest("CANNOT_CHAT_WITH_SELF", "Choose someone else");
		}
		requireWorkspaceMember(workspaceId, userId, "WORKSPACE_NOT_FOUND");
		requireWorkspaceMember(workspaceId, otherId, "USER_NOT_IN_WORKSPACE");
		String key = (userId.compareTo(otherId) < 0) ? userId + ":" + otherId : otherId + ":" + userId;
		Conversation c = conversations.findByDirectKey(key).orElseGet(() -> {
			Conversation created = conversations.saveAndFlush(Conversation.direct(workspaceId, key, userId));
			addMember(created.getId(), userId);
			addMember(created.getId(), otherId);
			return created;
		});
		return summary(userId, c);
	}

	public ConversationSummary createGroup(UUID userId, UUID workspaceId, String title, List<UUID> memberIds) {
		requireWorkspaceMember(workspaceId, userId, "WORKSPACE_NOT_FOUND");
		Set<UUID> everyone = new LinkedHashSet<>(memberIds);
		everyone.add(userId);
		if (everyone.size() > MAX_GROUP) {
			throw ApiException.badRequest("GROUP_TOO_LARGE", "A group has at most " + MAX_GROUP + " people");
		}
		everyone.forEach((id) -> requireWorkspaceMember(workspaceId, id, "USER_NOT_IN_WORKSPACE"));
		Conversation c = conversations.saveAndFlush(Conversation.group(workspaceId, title.strip(), userId));
		everyone.forEach((id) -> addMember(c.getId(), id));
		return summary(userId, c);
	}

	public ConversationSummary rename(UUID userId, UUID conversationId, String title) {
		Conversation c = require(userId, conversationId);
		requireGroup(c);
		c.rename(title.strip());
		return summary(userId, c);
	}

	public ConversationSummary addMembers(UUID userId, UUID conversationId, List<UUID> newMembers) {
		Conversation c = require(userId, conversationId);
		requireGroup(c);
		Set<UUID> current = new LinkedHashSet<>();
		members.findAllByConversationId(conversationId).forEach((m) -> current.add(m.getUserId()));
		for (UUID id : new LinkedHashSet<>(newMembers)) {
			requireWorkspaceMember(c.getWorkspaceId(), id, "USER_NOT_IN_WORKSPACE");
			if (current.add(id)) {
				if (current.size() > MAX_GROUP) {
					throw ApiException.badRequest("GROUP_TOO_LARGE", "A group has at most " + MAX_GROUP + " people");
				}
				addMember(conversationId, id);
			}
		}
		return summary(userId, c);
	}

	public void leave(UUID userId, UUID conversationId) {
		Conversation c = require(userId, conversationId);
		requireGroup(c);
		members.delete(members.findByConversationIdAndUserId(conversationId, userId).orElseThrow());
	}

	/** People who can read the conversation, for @mention suggestions. */
	@Transactional(readOnly = true)
	public List<UserRef> membersOf(UUID userId, UUID conversationId) {
		Conversation c = require(userId, conversationId);
		return mapper.userRefs(memberIds(c)).values().stream()
			.sorted(Comparator.comparing(UserRef::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	// ---- messages ----

	@Transactional(readOnly = true)
	public MessagePage messages(UUID userId, UUID conversationId, Instant before, int size) {
		require(userId, conversationId);
		int limit = Math.clamp(size, 1, MAX_PAGE);
		List<ChatMessage> page = messages.page(conversationId, (before == null) ? Instant.now().plusSeconds(86_400) : before,
				PageRequest.of(0, limit + 1));
		boolean more = page.size() > limit;
		List<ChatMessage> shown = new ArrayList<>(page.subList(0, Math.min(limit, page.size())));
		java.util.Collections.reverse(shown);
		return new MessagePage(mapper.responses(shown), more);
	}

	public MessageResponse send(UUID userId, UUID conversationId, ChatRequests.Send request) {
		Conversation c = require(userId, conversationId);
		Set<UUID> audience = memberIds(c);
		Set<UUID> mentioned = new LinkedHashSet<>((request.mentionIds() == null) ? List.of() : request.mentionIds());
		mentioned.remove(userId);
		if (!audience.containsAll(mentioned)) {
			throw ApiException.badRequest("MENTION_NOT_MEMBER", "Only people in this conversation can be mentioned");
		}
		if (request.replyToId() != null
				&& messages.findById(request.replyToId()).filter((m) -> m.getConversationId().equals(conversationId)).isEmpty()) {
			throw ApiException.badRequest("INVALID_REPLY", "The message you reply to is not in this conversation");
		}
		ChatMessage saved = messages.saveAndFlush(new ChatMessage(conversationId, userId, request.body().strip(), request.replyToId()));
		mentioned.forEach((id) -> mentions.save(new MessageMention(saved.getId(), id)));
		c.touch(saved.getCreatedAt());
		markReadAt(conversationId, userId, saved.getCreatedAt());

		MessageResponse response = mapper.response(saved);
		String sender = (response.sender() == null) ? "Ai đó" : response.sender().fullName();
		notifications.notifyAll(mentioned, NotificationType.MENTIONED, sender + " đã nhắc đến bạn", ChatMapper.snippet(saved.getBody()),
				"/chat/" + conversationId, c.getProjectId(), userId, null);
		afterCommit(() -> hub.sendToUsers(audience, event("chat.message", conversationId, "message", response)));
		return response;
	}

	public MessageResponse edit(UUID userId, UUID messageId, String body) {
		ChatMessage message = ownMessage(userId, messageId);
		message.edit(body.strip(), clock.instant());
		messages.flush();
		return broadcastUpdate(message);
	}

	public void delete(UUID userId, UUID messageId) {
		ChatMessage message = ownMessage(userId, messageId);
		message.delete(clock.instant());
		Conversation c = conversations.findById(message.getConversationId()).orElseThrow();
		Set<UUID> audience = memberIds(c);
		afterCommit(() -> hub.sendToUsers(audience, Map.of("type", "chat.message.deleted", "conversationId", c.getId(), "messageId", messageId)));
	}

	/** Adds the reaction, or removes it when the user already reacted with the same emoji. */
	public MessageResponse react(UUID userId, UUID messageId, String emoji) {
		ChatMessage message = messages.findById(messageId).orElseThrow(ChatService::messageNotFound);
		require(userId, message.getConversationId());
		if (message.getDeletedAt() != null) {
			throw messageNotFound();
		}
		String clean = emoji.strip();
		reactions.findByMessageIdAndUserIdAndEmoji(messageId, userId, clean)
			.ifPresentOrElse(reactions::delete, () -> reactions.save(new MessageReaction(messageId, userId, clean)));
		reactions.flush();
		return broadcastUpdate(message);
	}

	// ---- read state ----

	/** Marks everything up to now as read; returns the user's total unread afterwards. */
	public long markRead(UUID userId, UUID conversationId) {
		require(userId, conversationId);
		markReadAt(conversationId, userId, clock.instant());
		messages.flush();
		long total = unreadTotal(userId);
		afterCommit(() -> hub.sendToUser(userId, Map.of("type", "chat.read", "conversationId", conversationId, "unread", total)));
		return total;
	}

	public long unreadTotal(UUID userId) {
		List<UUID> ids = directory(userId).conversations().stream().map(Conversation::getId).toList();
		return unread(userId, ids).values().stream().mapToLong(Long::longValue).sum();
	}

	// ---- search ----

	@Transactional(readOnly = true)
	public List<SearchHit> search(UUID userId, String query, UUID conversationId) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		Directory directory = directoryReadOnly(userId);
		List<UUID> ids = (conversationId == null) ? directory.conversations().stream().map(Conversation::getId).toList()
				: directory.conversations().stream().map(Conversation::getId).filter(conversationId::equals).toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		String pattern = "%" + query.strip().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		List<ChatMessage> found = messages.search(ids, pattern, PageRequest.of(0, 30));
		Map<UUID, String> titles = new HashMap<>(directory.titles());
		List<MessageResponse> responses = mapper.responses(found);
		List<SearchHit> out = new ArrayList<>();
		for (MessageResponse r : responses) {
			out.add(new SearchHit(r, titles.getOrDefault(r.conversationId(), "")));
		}
		return out;
	}

	// ---- access ----

	Conversation require(UUID userId, UUID conversationId) {
		Conversation c = conversations.findById(conversationId).orElseThrow(ChatService::notFound);
		boolean allowed = switch (c.getKind()) {
			case PROJECT -> canSeeProject(userId, c.getProjectId());
			case TEAM -> teamMembers.existsByTeamIdAndUserId(c.getTeamId(), userId);
			case DIRECT, GROUP -> members.existsByConversationIdAndUserId(conversationId, userId);
		};
		if (!allowed) {
			throw notFound();
		}
		return c;
	}

	Set<UUID> memberIds(Conversation c) {
		return switch (c.getKind()) {
			case PROJECT -> new LinkedHashSet<>(projectMembers.findUserIds(c.getProjectId()));
			case TEAM -> new LinkedHashSet<>(teamMembers.findUserIds(c.getTeamId()));
			case DIRECT, GROUP -> {
				Set<UUID> ids = new LinkedHashSet<>();
				members.findAllByConversationId(c.getId()).forEach((m) -> ids.add(m.getUserId()));
				yield ids;
			}
		};
	}

	// ---- helpers ----

	private record Directory(List<Conversation> conversations, Map<UUID, String> titles) {
	}

	/** The user's conversations; when {@code create} is set, project/team channels that do not exist yet are created. */
	private Directory directory(UUID userId) {
		return buildDirectory(userId, true);
	}

	private Directory directoryReadOnly(UUID userId) {
		return buildDirectory(userId, false);
	}

	private Directory buildDirectory(UUID userId, boolean create) {
		List<Conversation> list = new ArrayList<>();
		Map<UUID, String> titles = new LinkedHashMap<>();
		for (Project project : projects.findOfMember(userId)) {
			Conversation c = create ? ensureProject(project) : conversations.findByProjectId(project.getId()).orElse(null);
			if (c != null) {
				if (create) {
					ensureRead(c.getId(), userId);
				}
				list.add(c);
				titles.put(c.getId(), project.getName());
			}
		}
		for (Team team : teams.findOfMember(userId)) {
			Conversation c = create ? ensureTeam(team) : conversations.findByTeamId(team.getId()).orElse(null);
			if (c != null) {
				if (create) {
					ensureRead(c.getId(), userId);
				}
				list.add(c);
				titles.put(c.getId(), team.getName());
			}
		}
		for (Conversation c : conversations.findOfMember(userId)) {
			list.add(c);
			titles.put(c.getId(), (c.getTitle() == null) ? "" : c.getTitle());
		}
		return new Directory(list, titles);
	}

	private ConversationSummary summary(UUID userId, Conversation c) {
		return list(userId).stream().filter((s) -> s.id().equals(c.getId())).findFirst().orElseThrow();
	}

	private Map<UUID, Long> unread(UUID userId, Collection<UUID> ids) {
		Map<UUID, Long> out = new HashMap<>();
		if (!ids.isEmpty()) {
			messages.unreadCounts(userId, ids).forEach((row) -> out.put((UUID) row[0], ((Number) row[1]).longValue()));
		}
		return out;
	}

	private Conversation ensureProject(Project project) {
		return conversations.findByProjectId(project.getId())
			.orElseGet(() -> conversations.saveAndFlush(Conversation.project(project.getWorkspaceId(), project.getId())));
	}

	private Conversation ensureTeam(Team team) {
		return conversations.findByTeamId(team.getId())
			.orElseGet(() -> conversations.saveAndFlush(Conversation.team(team.getWorkspaceId(), team.getId())));
	}

	private void ensureRead(UUID conversationId, UUID userId) {
		if (reads.findByConversationIdAndUserId(conversationId, userId).isEmpty()) {
			reads.save(new ConversationRead(conversationId, userId, clock.instant()));
		}
	}

	private void markReadAt(UUID conversationId, UUID userId, Instant at) {
		ConversationRead read = reads.findByConversationIdAndUserId(conversationId, userId).orElse(null);
		if (read == null) {
			reads.save(new ConversationRead(conversationId, userId, at));
		}
		else if (at.isAfter(read.getLastReadAt())) {
			read.readUntil(at);
		}
	}

	private void addMember(UUID conversationId, UUID userId) {
		members.save(new ConversationMember(conversationId, userId));
		// Whatever is said from now on is unread for them.
		reads.save(new ConversationRead(conversationId, userId, clock.instant()));
	}

	private ChatMessage ownMessage(UUID userId, UUID messageId) {
		ChatMessage message = messages.findById(messageId).orElseThrow(ChatService::messageNotFound);
		require(userId, message.getConversationId());
		if (message.getDeletedAt() != null) {
			throw messageNotFound();
		}
		if (!message.getSenderId().equals(userId)) {
			throw ApiException.forbidden("FORBIDDEN", "You can only change your own messages");
		}
		return message;
	}

	private MessageResponse broadcastUpdate(ChatMessage message) {
		MessageResponse response = mapper.response(message);
		Conversation c = conversations.findById(message.getConversationId()).orElseThrow();
		Set<UUID> audience = memberIds(c);
		afterCommit(() -> hub.sendToUsers(audience, event("chat.message.updated", c.getId(), "message", response)));
		return response;
	}

	private void requireWorkspaceMember(UUID workspaceId, UUID userId, String code) {
		if (!workspaceMembers.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
			throw ApiException.notFound(code, "Not a member of this workspace");
		}
	}

	private boolean canSeeProject(UUID userId, UUID projectId) {
		try {
			access.project(userId, projectId);
			return true;
		}
		catch (ApiException ex) {
			return false;
		}
	}

	private static void requireGroup(Conversation c) {
		if (c.getKind() != ConversationKind.GROUP) {
			throw ApiException.badRequest("NOT_A_GROUP", "Only group conversations can be changed this way");
		}
	}

	private static Map<String, Object> event(String type, UUID conversationId, String key, Object payload) {
		return Map.of("type", type, "conversationId", conversationId, key, payload);
	}

	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found");
	}

	private static ApiException messageNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message not found");
	}

}
