package com.workhub.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the audit log from growing for ever: entries older than {@code workhub.audit.retention-days} (default two
 * years, 0 = keep everything) are deleted by a daily job. This is the only place that removes audit rows.
 */
@Service
public class AuditRetentionService {

	private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

	private final AuditRepository entries;

	private final Clock clock;

	private final int retentionDays;

	AuditRetentionService(AuditRepository entries, Clock clock, @Value("${workhub.audit.retention-days:730}") int retentionDays) {
		this.entries = entries;
		this.clock = clock;
		this.retentionDays = retentionDays;
	}

	/** @return the number of entries deleted */
	@Transactional
	public int purge() {
		if (retentionDays <= 0) {
			return 0;
		}
		int deleted = entries.deleteOlderThan(Instant.now(clock).minus(Duration.ofDays(retentionDays)));
		if (deleted > 0) {
			log.info("Audit retention: deleted {} entries older than {} days", deleted, retentionDays);
		}
		return deleted;
	}

}
