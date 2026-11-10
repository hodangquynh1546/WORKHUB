package com.workhub.chat;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_mentions")
public class MessageMention {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	protected MessageMention() {
	}

	MessageMention(UUID messageId, UUID userId) {
		this.messageId = messageId;
		this.userId = userId;
	}

	public UUID getMessageId() {
		return messageId;
	}

	public UUID getUserId() {
		return userId;
	}

}
