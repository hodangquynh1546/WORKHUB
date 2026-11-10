package com.workhub.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findAllByUserIdOrderByCreatedAtDescIdAsc(UUID userId, Pageable pageable);

	Page<Notification> findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdAsc(UUID userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(UUID userId);

	boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

}
