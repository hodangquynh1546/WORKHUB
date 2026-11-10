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

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

	List<MessageReaction> findAllByMessageIdIn(Collection<UUID> messageIds);

	Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

}
