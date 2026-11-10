package com.workhub.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.workhub.notification.NotificationResponses.NotificationPage;
import com.workhub.notification.NotificationResponses.PreferenceResponse;
import com.workhub.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

	public record SetPreference(@NotNull NotificationType type, boolean inApp, boolean email) {
	}

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping("/api/notifications")
	public NotificationPage list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
		return notifications.list(CurrentUser.id(jwt), unread, page, size);
	}

	@GetMapping("/api/notifications/unread-count")
	public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("unread", notifications.unreadCount(CurrentUser.id(jwt)));
	}

	@PostMapping("/api/notifications/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		notifications.markRead(CurrentUser.id(jwt), id);
	}

	@PostMapping("/api/notifications/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		notifications.markAllRead(CurrentUser.id(jwt));
	}

	@GetMapping("/api/notification-preferences")
	public List<PreferenceResponse> preferences(@AuthenticationPrincipal Jwt jwt) {
		return notifications.preferences(CurrentUser.id(jwt));
	}

	@PutMapping("/api/notification-preferences")
	public List<PreferenceResponse> setPreference(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SetPreference request) {
		return notifications.setPreference(CurrentUser.id(jwt), request.type(), request.inApp(), request.email());
	}

}
