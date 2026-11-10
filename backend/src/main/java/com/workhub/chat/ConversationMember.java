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

/** Membership of a DIRECT or GROUP conversation. */
@Entity
@Table(name = "conversation_members")
public class ConversationMember {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected ConversationMember() {
	}

	ConversationMember(UUID conversationId, UUID userId) {
		this.conversationId = conversationId;
		this.userId = userId;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public UUID getUserId() {
		return userId;
	}

}
