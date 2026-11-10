package com.workhub.alert;

import java.util.UUID;

/** {@code kind} is the notification type name (ALERT_OVERDUE, TASK_BLOCKED, …); severity is INFO, WARNING or CRITICAL. */
public record AlertResponse(String kind, String severity, String title, String detail, String link, UUID subjectId) {
}
