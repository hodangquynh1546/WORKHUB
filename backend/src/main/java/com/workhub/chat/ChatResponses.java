package com.workhub.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.IssueResponses.UserRef;

public final class ChatResponses {

	private ChatResponses() {
	}

	public record MessagePreview(UUID senderId, String senderName, String snippet, Instant createdAt) {
	}

	/** {@code title} is the project/team name, the other person's name (DIRECT) or the group's title. */
	public record ConversationSummary(UUID id, ConversationKind kind, String title, UUID projectId, UUID teamId,
			UserRef other, int memberCount, MessagePreview last, long unread, Instant lastMessageAt) {
	}

	public record ReplyPreview(UUID id, String senderName, String snippet, boolean deleted) {
	}

	/** Who reacted with this emoji; the client works out which ones are its own. */
	public record ReactionResponse(String emoji, List<UUID> userIds) {
	}

	/** {@code body} is null for a deleted message. */
	public record MessageResponse(UUID id, UUID conversationId, UserRef sender, String body, ReplyPreview replyTo,
			Instant createdAt, Instant editedAt, boolean deleted, List<ReactionResponse> reactions, List<UUID> mentions) {
	}

	public record MessagePage(List<MessageResponse> items, boolean hasMore) {
	}

	public record SearchHit(MessageResponse message, String conversationTitle) {
	}

}
