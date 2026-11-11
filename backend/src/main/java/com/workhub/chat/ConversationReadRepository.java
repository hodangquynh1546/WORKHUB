package com.workhub.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationReadRepository extends JpaRepository<ConversationRead, UUID> {

	Optional<ConversationRead> findByConversationIdAndUserId(UUID conversationId, UUID userId);

	List<ConversationRead> findAllByConversationId(UUID conversationId);

	List<ConversationRead> findAllByUserIdAndConversationIdIn(UUID userId, Collection<UUID> conversationIds);

}
