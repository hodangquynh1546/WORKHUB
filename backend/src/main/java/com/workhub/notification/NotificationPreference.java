package com.workhub.notification;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A user's override of the defaults for one notification type. */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private NotificationType type;

	@Column(name = "in_app", nullable = false)
	private boolean inApp;

	@Column(nullable = false)
	private boolean email;

	protected NotificationPreference() {
	}

	NotificationPreference(UUID userId, NotificationType type, boolean inApp, boolean email) {
		this.userId = userId;
		this.type = type;
		this.inApp = inApp;
		this.email = email;
	}

	void set(boolean inApp, boolean email) {
		this.inApp = inApp;
		this.email = email;
	}

	public NotificationType getType() {
		return type;
	}

	public boolean isInApp() {
		return inApp;
	}

	public boolean isEmail() {
		return email;
	}

}
