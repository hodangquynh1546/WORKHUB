package com.workhub.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/** A chat message. Deleting only stamps {@code deletedAt}; the API then hides the text. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	public static final int MAX_BODY = 4000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Column(name = "sender_id", nullable = false, updatable = false)
	private UUID senderId;

	@Column(nullable = false, length = MAX_BODY)
	private String body;

	@Column(name = "reply_to_id", updatable = false)
	private UUID replyToId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "edited_at")
	private Instant editedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected ChatMessage() {
	}

	ChatMessage(UUID conversationId, UUID senderId, String body, UUID replyToId) {
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.body = body;
		this.replyToId = replyToId;
	}

	void edit(String body, Instant now) {
		this.body = body;
		this.editedAt = now;
	}

	void delete(Instant now) {
		this.deletedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public UUID getSenderId() {
		return senderId;
	}

	public String getBody() {
		return body;
	}

	public UUID getReplyToId() {
		return replyToId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEditedAt() {
		return editedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
