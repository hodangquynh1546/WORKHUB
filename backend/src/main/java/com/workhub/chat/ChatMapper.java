package com.workhub.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.chat.ChatResponses.AttachmentResponse;
import com.workhub.chat.ChatResponses.ReactionResponse;
import com.workhub.chat.ChatResponses.ReplyPreview;
import com.workhub.chat.ChatResponses.MessageResponse;
import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Messages to responses with a fixed number of queries per call. */
@Component
@Transactional(readOnly = true)
class ChatMapper {

	private static final int SNIPPET = 80;

	private final UserRepository users;

	private final MessageReactionRepository reactions;

	private final MessageMentionRepository mentions;

	private final ChatMessageRepository messages;

	private final ChatAttachmentRepository attachments;

	ChatMapper(UserRepository users, MessageReactionRepository reactions, MessageMentionRepository mentions, ChatMessageRepository messages,
			ChatAttachmentRepository attachments) {
		this.attachments = attachments;
		this.users = users;
		this.reactions = reactions;
		this.mentions = mentions;
		this.messages = messages;
	}

	List<MessageResponse> responses(List<ChatMessage> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = list.stream().map(ChatMessage::getId).toList();
		Map<UUID, List<MessageReaction>> reactionsByMessage = new HashMap<>();
		reactions.findAllByMessageIdIn(ids).forEach((r) -> reactionsByMessage.computeIfAbsent(r.getMessageId(), (k) -> new ArrayList<>()).add(r));
		Map<UUID, List<UUID>> mentionsByMessage = new HashMap<>();
		mentions.findAllByMessageIdIn(ids).forEach((m) -> mentionsByMessage.computeIfAbsent(m.getMessageId(), (k) -> new ArrayList<>()).add(m.getUserId()));

		Map<UUID, List<AttachmentResponse>> files = new HashMap<>();
		attachments.infoFor(ids).forEach((a) -> files.computeIfAbsent(a.messageId(), (k) -> new ArrayList<>()).add(attachment(a)));

		Set<UUID> replyIds = new HashSet<>();
		list.forEach((m) -> {
			if (m.getReplyToId() != null) {
				replyIds.add(m.getReplyToId());
			}
		});
		Map<UUID, ChatMessage> replies = new HashMap<>();
		messages.findAllById(replyIds).forEach((m) -> replies.put(m.getId(), m));

		Set<UUID> senders = new HashSet<>();
		list.forEach((m) -> senders.add(m.getSenderId()));
		replies.values().forEach((m) -> senders.add(m.getSenderId()));
		Map<UUID, User> people = new HashMap<>();
		users.findAllById(senders).forEach((u) -> people.put(u.getId(), u));

		List<MessageResponse> out = new ArrayList<>();
		for (ChatMessage m : list) {
			boolean deleted = m.getDeletedAt() != null;
			ChatMessage reply = replies.get(m.getReplyToId());
			ReplyPreview preview = (reply == null) ? null
					: new ReplyPreview(reply.getId(), name(people.get(reply.getSenderId())),
							(reply.getDeletedAt() != null) ? null : snippet(reply.getBody()), reply.getDeletedAt() != null);
			out.add(new MessageResponse(m.getId(), m.getConversationId(), ref(people.get(m.getSenderId())),
					deleted ? null : m.getBody(), preview, m.getCreatedAt(), m.getEditedAt(), deleted,
					deleted ? List.of() : reactionList(reactionsByMessage.get(m.getId())),
					deleted ? List.of() : mentionsByMessage.getOrDefault(m.getId(), List.of()),
					deleted ? List.of() : files.getOrDefault(m.getId(), List.of())));
		}
		return out;
	}

	static final java.util.Set<String> IMAGE_TYPES = java.util.Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

	private static AttachmentResponse attachment(ChatAttachment.Info a) {
		return new AttachmentResponse(a.id(), a.fileName(), a.contentType(), a.sizeBytes(), IMAGE_TYPES.contains(a.contentType()));
	}

	MessageResponse response(ChatMessage message) {
		return responses(List.of(message)).get(0);
	}

	Map<UUID, UserRef> userRefs(Collection<UUID> ids) {
		Map<UUID, UserRef> out = new HashMap<>();
		users.findAllById(new HashSet<>(ids)).forEach((u) -> out.put(u.getId(), IssueMapper.userRef(u)));
		return out;
	}

	static String snippet(String body) {
		String flat = body.replaceAll("\\s+", " ").strip();
		return (flat.length() <= SNIPPET) ? flat : flat.substring(0, SNIPPET - 1) + "…";
	}

	private static List<ReactionResponse> reactionList(List<MessageReaction> list) {
		if (list == null) {
			return List.of();
		}
		Map<String, List<UUID>> byEmoji = new LinkedHashMap<>();
		list.forEach((r) -> byEmoji.computeIfAbsent(r.getEmoji(), (k) -> new ArrayList<>()).add(r.getUserId()));
		return byEmoji.entrySet().stream().map((e) -> new ReactionResponse(e.getKey(), e.getValue())).toList();
	}

	private static UserRef ref(User user) {
		return (user == null) ? null : IssueMapper.userRef(user);
	}

	private static String name(User user) {
		return (user == null) ? "?" : user.getFullName();
	}

}
