package com.workhub.notification;

/**
 * Everything that can notify a user. {@code emailByDefault} is only the starting point: each user can switch each
 * type's in-app and email delivery on or off.
 */
public enum NotificationType {

	ISSUE_ASSIGNED(true, false),
	ISSUE_STATUS_CHANGED(false, false),
	ISSUE_COMMENTED(false, false),
	MENTIONED(true, false),
	INVITED_TO_TEAM(true, false),
	INVITED_TO_PROJECT(true, false),
	MEETING_INVITED(true, false),
	MEETING_CANCELLED(true, false),
	MEETING_REMINDER(true, false),
	SPRINT_STARTED(false, false),
	SPRINT_COMPLETED(false, false),
	TASK_BLOCKED(true, true),
	ALERT_OVERDUE(true, true),
	ALERT_DUE_SOON(false, true),
	ALERT_SPRINT_AT_RISK(true, true),
	ALERT_SCOPE_CHANGE(false, true),
	ALERT_OVERLOAD(false, true),
	ALERT_NO_PROGRESS(false, true);

	private final boolean emailByDefault;

	private final boolean alert;

	NotificationType(boolean emailByDefault, boolean alert) {
		this.emailByDefault = emailByDefault;
		this.alert = alert;
	}

	public boolean emailByDefault() {
		return emailByDefault;
	}

	/** Alerts are raised by rules about progress; the rest report something that happened. */
	public boolean isAlert() {
		return alert;
	}

}
