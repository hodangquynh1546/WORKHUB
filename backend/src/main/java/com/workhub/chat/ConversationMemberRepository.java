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

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

	List<ConversationMember> findAllByConversationId(UUID conversationId);

	Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

	boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

	long countByConversationId(UUID conversationId);

}
