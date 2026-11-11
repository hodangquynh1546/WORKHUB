package com.workhub.alert;

import com.workhub.audit.AuditRetentionService;
import com.workhub.meeting.MeetingReminderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The background jobs: progress alerts every hour (at minute 0) and meeting reminders every minute. Turn all of
 * them off with {@code workhub.scheduling.enabled=false} (the tests do, and call the services directly).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "workhub.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class Schedulers {

	private static final Logger log = LoggerFactory.getLogger(Schedulers.class);

	private final AlertService alerts;

	private final MeetingReminderService reminders;

	private final AuditRetentionService auditRetention;

	Schedulers(AlertService alerts, MeetingReminderService reminders, AuditRetentionService auditRetention) {
		this.auditRetention = auditRetention;
		this.alerts = alerts;
		this.reminders = reminders;
	}

	@Scheduled(cron = "${workhub.alerts.cron:0 0 * * * *}")
	void progressAlerts() {
		log.debug("Progress alerts: {} notifications", alerts.run());
	}

	@Scheduled(fixedDelayString = "${workhub.reminders.interval:60000}", initialDelay = 30000)
	void meetingReminders() {
		reminders.run();
	}

	@Scheduled(cron = "${workhub.audit.purge-cron:0 15 3 * * *}")
	void auditRetention() {
		auditRetention.purge();
	}

}
