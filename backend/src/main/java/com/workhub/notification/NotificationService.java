package com.workhub.notification;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.common.PageResponse;
import com.workhub.notification.NotificationDelivery.Delivery;
import com.workhub.notification.NotificationResponses.NotificationPage;
import com.workhub.notification.NotificationResponses.NotificationResponse;
import com.workhub.notification.NotificationResponses.PreferenceResponse;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications for other modules and serves the notification center. {@code notify} runs inside the
 * caller's transaction (so a rolled-back change leaves no notification) and the push/email follow after commit.
 * Nobody is notified about their own actions, and a (user, dedupe key) pair is delivered at most once.
 */
@Service
@Transactional
public class NotificationService {

	private static final int TITLE_MAX = 300;

	private static final int BODY_MAX = 1000;

	private final NotificationRepository notifications;

	private final NotificationPreferenceRepository preferences;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
			ApplicationEventPublisher events, Clock clock) {
		this.notifications = notifications;
		this.preferences = preferences;
		this.events = events;
		this.clock = clock;
	}

	// ---- creating ----

	public void notify(UUID recipientId, NotificationType type, String title, String body, String link, UUID projectId,
			UUID actorId, String dedupeKey) {
		if (recipientId == null || recipientId.equals(actorId)) {
			return;
		}
		if (dedupeKey != null && notifications.existsByUserIdAndDedupeKey(recipientId, dedupeKey)) {
			return;
		}
		Channels channels = channels(recipientId, type);
		if (!channels.inApp() && !channels.email()) {
			return;
		}
		String cleanTitle = cut(title, TITLE_MAX);
		String cleanBody = cut(body, BODY_MAX);
		NotificationResponse payload = null;
		long unread = 0;
		if (channels.inApp()) {
			Notification saved = notifications.saveAndFlush(
					new Notification(recipientId, type, cleanTitle, cleanBody, link, projectId, actorId, dedupeKey));
			payload = NotificationResponse.of(saved);
			unread = notifications.countByUserIdAndReadAtIsNull(recipientId);
		}
		events.publishEvent(new Delivery(recipientId, payload, unread, channels.email(), cleanTitle,
				(cleanBody == null) ? cleanTitle : cleanTitle + "\n" + cleanBody, link));
	}

	public void notifyAll(Collection<UUID> recipients, NotificationType type, String title, String body, String link,
			UUID projectId, UUID actorId, String dedupeKey) {
		for (UUID id : new java.util.LinkedHashSet<>(recipients)) {
			notify(id, type, title, body, link, projectId, actorId, dedupeKey);
		}
	}

	// ---- reading ----

	@Transactional(readOnly = true)
	public NotificationPage list(UUID userId, boolean unreadOnly, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100));
		Page<Notification> result = unreadOnly ? notifications.findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdAsc(userId, pageable)
				: notifications.findAllByUserIdOrderByCreatedAtDescIdAsc(userId, pageable);
		return new NotificationPage(result.map(NotificationResponse::of).getContent(), result.getNumber(), result.getSize(),
				result.getTotalElements(), notifications.countByUserIdAndReadAtIsNull(userId));
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID userId) {
		return notifications.countByUserIdAndReadAtIsNull(userId);
	}

	public void markRead(UUID userId, UUID notificationId) {
		Notification n = notifications.findById(notificationId)
			.filter((x) -> x.getUserId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found"));
		n.markRead(clock.instant());
	}

	public void markAllRead(UUID userId) {
		notifications.markAllRead(userId, clock.instant());
	}

	// ---- preferences ----

	@Transactional(readOnly = true)
	public List<PreferenceResponse> preferences(UUID userId) {
		Map<NotificationType, NotificationPreference> overrides = new EnumMap<>(NotificationType.class);
		preferences.findAllByUserId(userId).forEach((p) -> overrides.put(p.getType(), p));
		return java.util.Arrays.stream(NotificationType.values()).map((type) -> {
			NotificationPreference p = overrides.get(type);
			return new PreferenceResponse(type, type.isAlert(), (p == null) || p.isInApp(), (p == null) ? type.emailByDefault() : p.isEmail());
		}).toList();
	}

	public List<PreferenceResponse> setPreference(UUID userId, NotificationType type, boolean inApp, boolean email) {
		NotificationPreference p = preferences.findByUserIdAndType(userId, type)
			.orElseGet(() -> new NotificationPreference(userId, type, inApp, email));
		p.set(inApp, email);
		preferences.saveAndFlush(p);
		return preferences(userId);
	}

	// ---- helpers ----

	private record Channels(boolean inApp, boolean email) {
	}

	private Channels channels(UUID userId, NotificationType type) {
		return preferences.findByUserIdAndType(userId, type)
			.map((p) -> new Channels(p.isInApp(), p.isEmail()))
			.orElseGet(() -> new Channels(true, type.emailByDefault()));
	}

	private static String cut(String text, int max) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String clean = text.strip();
		return (clean.length() <= max) ? clean : clean.substring(0, max - 1) + "…";
	}

}
