package com.workhub.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How far a user has read a conversation. A conversation without a row for the user has nothing unread for them. */
@Entity
@Table(name = "conversation_reads")
public class ConversationRead {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "last_read_at", nullable = false)
	private Instant lastReadAt;

	protected ConversationRead() {
	}

	ConversationRead(UUID conversationId, UUID userId, Instant lastReadAt) {
		this.conversationId = conversationId;
		this.userId = userId;
		this.lastReadAt = lastReadAt;
	}

	void readUntil(Instant at) {
		this.lastReadAt = at;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public Instant getLastReadAt() {
		return lastReadAt;
	}

}
