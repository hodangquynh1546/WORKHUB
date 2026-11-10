package com.workhub.alert;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectPermission;
import com.workhub.alert.AlertSettings.Values;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes a project's alert thresholds; anyone who sees the project reads, UPDATE_PROJECT writes. */
@Service
@Transactional
public class AlertSettingsService {

	/** The same shape the settings are stored in, with limits for what a manager may enter. */
	public record Request(boolean overdueEnabled, @Min(0) @Max(30) int dueSoonDays, @Min(0) @Max(30) int sprintRiskDaysLeft,
			@Min(0) @Max(100) int sprintRiskOpenPct, boolean blockedEnabled, @Min(0) @Max(200) int overloadOpenIssues,
			@Min(0) @Max(500) int scopeChangePct, @Min(0) @Max(90) int noProgressDays) {

		Values toValues() {
			return new Values(overdueEnabled, dueSoonDays, sprintRiskDaysLeft, sprintRiskOpenPct, blockedEnabled,
					overloadOpenIssues, scopeChangePct, noProgressDays);
		}
	}

	private final AccessService access;

	private final AlertSettingsRepository settings;

	AlertSettingsService(AccessService access, AlertSettingsRepository settings) {
		this.access = access;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public Values get(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		return valuesOf(projectId);
	}

	public Values update(UUID userId, UUID projectId, Request request) {
		access.project(userId, projectId).require(ProjectPermission.UPDATE_PROJECT).requireActive();
		AlertSettings row = settings.findById(projectId).orElseGet(() -> new AlertSettings(projectId, Values.defaults()));
		row.apply(request.toValues());
		settings.saveAndFlush(row);
		return row.values();
	}

	/** For the evaluator and for modules that ask whether one rule is on; no permission check. */
	@Transactional(readOnly = true)
	public Values valuesOf(UUID projectId) {
		return settings.findById(projectId).map(AlertSettings::values).orElseGet(Values::defaults);
	}

}
