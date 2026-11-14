package com.workhub.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Delivers the integration outbox every second and purges it daily; off with {@code workhub.scheduling.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "workhub.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class IntegrationJobs {

	private final IntegrationOutbox outbox;

	IntegrationJobs(IntegrationOutbox outbox) {
		this.outbox = outbox;
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 5000)
	void deliver() {
		outbox.drain();
	}

	@Scheduled(cron = "0 30 3 * * *")
	void purge() {
		outbox.purge();
	}

}
