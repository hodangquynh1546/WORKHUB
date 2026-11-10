package com.workhub.alert;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.alert.AlertSettings.Values;
import com.workhub.security.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {

	private final AlertService alerts;

	private final AlertSettingsService settings;

	public AlertController(AlertService alerts, AlertSettingsService settings) {
		this.alerts = alerts;
		this.settings = settings;
	}

	@GetMapping("/api/projects/{projectId}/alerts")
	public List<AlertResponse> alerts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return alerts.current(CurrentUser.id(jwt), projectId);
	}

	@GetMapping("/api/projects/{projectId}/alert-settings")
	public Values getSettings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return settings.get(CurrentUser.id(jwt), projectId);
	}

	@PutMapping("/api/projects/{projectId}/alert-settings")
	public Values updateSettings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody AlertSettingsService.Request request) {
		return settings.update(CurrentUser.id(jwt), projectId, request);
	}

}
