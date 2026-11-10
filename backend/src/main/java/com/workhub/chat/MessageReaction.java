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

@Entity
@Table(name = "message_reactions")
public class MessageReaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 16)
	private String emoji;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MessageReaction() {
	}

	MessageReaction(UUID messageId, UUID userId, String emoji) {
		this.messageId = messageId;
		this.userId = userId;
		this.emoji = emoji;
	}

	public UUID getMessageId() {
		return messageId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getEmoji() {
		return emoji;
	}

}
