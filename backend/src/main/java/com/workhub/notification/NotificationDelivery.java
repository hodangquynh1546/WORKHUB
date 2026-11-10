package com.workhub.notification;

import java.util.UUID;

import com.workhub.auth.EmailSender;
import com.workhub.notification.NotificationResponses.NotificationEvent;
import com.workhub.notification.NotificationResponses.NotificationResponse;
import com.workhub.realtime.RealtimeHub;
import com.workhub.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes a notification to the user's open sessions and emails it, but only after the transaction that created it
 * committed, so nobody is told about something that was rolled back. Failures here never affect the caller.
 */
@Component
class NotificationDelivery {

	private static final Logger log = LoggerFactory.getLogger(NotificationDelivery.class);

	/** {@code notification} is null when the user turned off in-app delivery but still wants the email. */
	record Delivery(UUID userId, NotificationResponse notification, long unread, boolean email, String subject,
			String text, String link) {
	}

	private final RealtimeHub hub;

	private final EmailSender emails;

	private final UserRepository users;

	private final String frontendBaseUrl;

	NotificationDelivery(RealtimeHub hub, EmailSender emails, UserRepository users,
			@Value("${workhub.auth.frontend-base-url}") String frontendBaseUrl) {
		this.hub = hub;
		this.emails = emails;
		this.users = users;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void on(Delivery delivery) {
		try {
			if (delivery.notification() != null) {
				hub.sendToUser(delivery.userId(), new NotificationEvent("notification", delivery.notification(), delivery.unread()));
			}
			if (delivery.email()) {
				users.findById(delivery.userId()).ifPresent((user) -> {
					String url = (delivery.link() == null) ? frontendBaseUrl : frontendBaseUrl + delivery.link();
					emails.send(user.getEmail(), delivery.subject(), delivery.text() + "\n\n" + url);
				});
			}
		}
		catch (RuntimeException ex) {
			log.warn("Could not deliver notification to {}", delivery.userId(), ex);
		}
	}

}
