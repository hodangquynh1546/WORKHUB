package com.workhub.alert;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Thresholds of a project's progress alerts. 0 switches a numeric rule off. */
@Entity
@Table(name = "project_alert_settings")
public class AlertSettings {

	@Id
	@Column(name = "project_id")
	private UUID projectId;

	@Column(name = "overdue_enabled", nullable = false)
	private boolean overdueEnabled;

	@Column(name = "due_soon_days", nullable = false)
	private int dueSoonDays;

	@Column(name = "sprint_risk_days_left", nullable = false)
	private int sprintRiskDaysLeft;

	@Column(name = "sprint_risk_open_pct", nullable = false)
	private int sprintRiskOpenPct;

	@Column(name = "blocked_enabled", nullable = false)
	private boolean blockedEnabled;

	@Column(name = "overload_open_issues", nullable = false)
	private int overloadOpenIssues;

	@Column(name = "scope_change_pct", nullable = false)
	private int scopeChangePct;

	@Column(name = "no_progress_days", nullable = false)
	private int noProgressDays;

	protected AlertSettings() {
	}

	AlertSettings(UUID projectId, Values values) {
		this.projectId = projectId;
		apply(values);
	}

	/** The values of every rule; also what the API reads and writes. */
	public record Values(boolean overdueEnabled, int dueSoonDays, int sprintRiskDaysLeft, int sprintRiskOpenPct,
			boolean blockedEnabled, int overloadOpenIssues, int scopeChangePct, int noProgressDays) {

		/** What a project gets until somebody changes it. */
		public static Values defaults() {
			return new Values(true, 2, 2, 40, true, 10, 20, 5);
		}
	}

	void apply(Values v) {
		this.overdueEnabled = v.overdueEnabled();
		this.dueSoonDays = v.dueSoonDays();
		this.sprintRiskDaysLeft = v.sprintRiskDaysLeft();
		this.sprintRiskOpenPct = v.sprintRiskOpenPct();
		this.blockedEnabled = v.blockedEnabled();
		this.overloadOpenIssues = v.overloadOpenIssues();
		this.scopeChangePct = v.scopeChangePct();
		this.noProgressDays = v.noProgressDays();
	}

	Values values() {
		return new Values(overdueEnabled, dueSoonDays, sprintRiskDaysLeft, sprintRiskOpenPct, blockedEnabled,
				overloadOpenIssues, scopeChangePct, noProgressDays);
	}

}
