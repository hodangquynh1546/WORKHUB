package com.workhub.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

	List<NotificationPreference> findAllByUserId(UUID userId);

	Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);

}
