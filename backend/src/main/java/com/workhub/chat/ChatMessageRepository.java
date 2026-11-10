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

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

	/** Newest first; {@code before} is exclusive (pass a far-future instant for the latest page). */
	@Query("select m from ChatMessage m where m.conversationId = :conversationId and m.createdAt < :before order by m.createdAt desc, m.id desc")
	List<ChatMessage> page(@Param("conversationId") UUID conversationId, @Param("before") Instant before, Pageable pageable);

	@Query("""
			select m from ChatMessage m
			where m.conversationId in :ids
				and m.createdAt = (select max(x.createdAt) from ChatMessage x where x.conversationId = m.conversationId)
			""")
	List<ChatMessage> latestIn(@Param("ids") Collection<UUID> ids);

	/** Rows: conversation id, unread count for {@code userId} (own and deleted messages never count). */
	@Query("""
			select m.conversationId, count(m) from ChatMessage m, ConversationRead r
			where r.conversationId = m.conversationId and r.userId = :userId and m.createdAt > r.lastReadAt
				and m.senderId <> :userId and m.deletedAt is null and m.conversationId in :ids
			group by m.conversationId
			""")
	List<Object[]> unreadCounts(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

	@Query("""
			select m from ChatMessage m
			where m.conversationId in :ids and m.deletedAt is null and lower(m.body) like :pattern escape '\\'
			order by m.createdAt desc, m.id desc
			""")
	List<ChatMessage> search(@Param("ids") Collection<UUID> ids, @Param("pattern") String pattern, Pageable pageable);

}
