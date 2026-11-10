package com.workhub.alert;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.notification.NotificationService;
import com.workhub.project.Project;
import com.workhub.project.ProjectRepository;
import com.workhub.project.ProjectStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Progress alerts. {@link #current} shows a project's risks right now (any project member may look);
 * {@link #run} is the scheduled job that turns them into notifications, once per (person, alert, day or change).
 */
@Service
public class AlertService {

	private static final Logger log = LoggerFactory.getLogger(AlertService.class);

	private final AccessService access;

	private final AlertEvaluator evaluator;

	private final NotificationService notifications;

	private final ProjectRepository projects;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final ZoneId zone;

	AlertService(AccessService access, AlertEvaluator evaluator, NotificationService notifications,
			ProjectRepository projects, PlatformTransactionManager transactionManager, Clock clock, ZoneId zone) {
		this.access = access;
		this.evaluator = evaluator;
		this.notifications = notifications;
		this.projects = projects;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.zone = zone;
	}

	@Transactional(readOnly = true)
	public List<AlertResponse> current(UUID userId, UUID projectId) {
		Project project = access.project(userId, projectId).project();
		return evaluator.evaluate(project, today(), clock.instant()).stream().map(AlertItem::toResponse).toList();
	}

	/** Evaluates every live project and notifies; one project failing never stops the others. Returns notifications attempted. */
	public int run() {
		int attempted = 0;
		for (Project project : projects.findAll()) {
			if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.COMPLETED) {
				continue;
			}
			try {
				Integer count = transaction.execute((status) -> notifyProject(project));
				attempted += (count == null) ? 0 : count;
			}
			catch (RuntimeException ex) {
				log.warn("Alert run failed for project {}", project.getId(), ex);
			}
		}
		return attempted;
	}

	private int notifyProject(Project project) {
		int attempted = 0;
		for (AlertItem alert : evaluator.evaluate(project, today(), clock.instant())) {
			notifications.notifyAll(alert.recipients(), alert.type(), alert.title(), alert.detail(), alert.link(),
					project.getId(), null, alert.dedupeKey());
			attempted += alert.recipients().size();
		}
		return attempted;
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

}
