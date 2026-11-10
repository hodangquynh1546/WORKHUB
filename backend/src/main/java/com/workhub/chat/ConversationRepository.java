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

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	Optional<Conversation> findByProjectId(UUID projectId);

	Optional<Conversation> findByTeamId(UUID teamId);

	Optional<Conversation> findByDirectKey(String directKey);

	@Query("select c from Conversation c join ConversationMember m on m.conversationId = c.id where m.userId = :userId")
	List<Conversation> findOfMember(@Param("userId") UUID userId);

}
