package com.workhub.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationResponses {

	private NotificationResponses() {
	}

	public record NotificationResponse(UUID id, NotificationType type, boolean alert, String title, String body,
			String link, UUID projectId, UUID actorId, boolean read, Instant createdAt) {

		static NotificationResponse of(Notification n) {
			return new NotificationResponse(n.getId(), n.getType(), n.getType().isAlert(), n.getTitle(), n.getBody(),
					n.getLink(), n.getProjectId(), n.getActorId(), n.getReadAt() != null, n.getCreatedAt());
		}
	}

	public record NotificationPage(List<NotificationResponse> items, int page, int size, long total, long unread) {
	}

	/** What a user receives for one type, and where. */
	public record PreferenceResponse(NotificationType type, boolean alert, boolean inApp, boolean email) {
	}

	/** Pushed over the WebSocket as {@code {"type":"notification", ...}} when a notification is created. */
	public record NotificationEvent(String type, NotificationResponse notification, long unread) {
	}

}
