package com.workhub.alert;

import java.util.Set;
import java.util.UUID;

import com.workhub.notification.NotificationType;

/**
 * One finding of the progress rules. {@code recipients} and {@code dedupeKey} are for the notification job only; the
 * API returns {@link AlertResponse}. These are risk signals for people to look at, not verdicts.
 */
record AlertItem(NotificationType type, String severity, String title, String detail, String link, UUID subjectId,
		Set<UUID> recipients, String dedupeKey) {

	AlertResponse toResponse() {
		return new AlertResponse(type.name(), severity, title, detail, link, subjectId);
	}

}
