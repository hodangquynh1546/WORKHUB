package com.workhub.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Durable, coalescing "project changed" notifications for integrations. {@link #projectChanged} is called after any
 * successful write to a project that has issues linked to another system; {@link #drain} (run by a scheduler) delivers
 * the pending rows to {@code workhub.integration.webhook-url}, signed with HMAC-SHA256, retrying with backoff. The
 * receiver reads the current state, so a lost or merged notification costs nothing.
 */
@Service
public class IntegrationOutbox {

	private static final Logger log = LoggerFactory.getLogger(IntegrationOutbox.class);

	static final int MAX_ATTEMPTS = 12;

	private final JdbcTemplate jdbc;

	private final IntegrationProperties properties;

	private final Clock clock;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public IntegrationOutbox(JdbcTemplate jdbc, IntegrationProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	/** Cheap no-op unless webhooks are configured and the project has (or had) externally linked issues. */
	public void projectChanged(UUID projectId) {
		if (!properties.webhookEnabled()) {
			return;
		}
		Boolean linked = jdbc.queryForObject(
				"select count(*) > 0 from issues where project_id = ? and external_ref is not null", Boolean.class, projectId);
		if (!Boolean.TRUE.equals(linked)) {
			return;
		}
		Integer pending = jdbc.queryForObject(
				"select count(*) from integration_outbox where project_id = ? and delivered_at is null and attempts < ?",
				Integer.class, projectId, MAX_ATTEMPTS);
		if (pending != null && pending > 0) {
			// Already queued: make sure it is retried soon rather than after a long backoff.
			jdbc.update("update integration_outbox set next_attempt_at = ? where project_id = ? and delivered_at is null and attempts < ?",
					Timestamp.from(clock.instant()), projectId, MAX_ATTEMPTS);
			return;
		}
		Instant now = clock.instant();
		jdbc.update("insert into integration_outbox (id, project_id, created_at, next_attempt_at, attempts) values (?, ?, ?, ?, 0)",
				UUID.randomUUID(), projectId, Timestamp.from(now), Timestamp.from(now));
	}

	/** Delivers what is due. Returns the number of notifications delivered. */
	public int drain() {
		if (!properties.webhookEnabled()) {
			return 0;
		}
		Instant now = clock.instant();
		List<UUID[]> due = jdbc.query(
				"select id, project_id from integration_outbox where delivered_at is null and attempts < ? and next_attempt_at <= ? order by created_at limit 50",
				(rs, i) -> new UUID[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class) }, MAX_ATTEMPTS, Timestamp.from(now));
		int delivered = 0;
		for (UUID[] row : due) {
			if (deliver(row[1])) {
				jdbc.update("update integration_outbox set delivered_at = ?, attempts = attempts + 1 where id = ?",
						Timestamp.from(clock.instant()), row[0]);
				delivered++;
			}
			else {
				Integer attempts = jdbc.queryForObject("select attempts from integration_outbox where id = ?", Integer.class, row[0]);
				long backoff = Math.min(300, 1L << Math.min(attempts == null ? 0 : attempts, 8));
				jdbc.update("update integration_outbox set attempts = attempts + 1, next_attempt_at = ? where id = ?",
						Timestamp.from(clock.instant().plusSeconds(backoff)), row[0]);
			}
		}
		return delivered;
	}

	/** Forgets delivered rows after a week. */
	public void purge() {
		jdbc.update("delete from integration_outbox where delivered_at is not null and delivered_at < ?",
				Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
	}

	private boolean deliver(UUID projectId) {
		String payload = "{\"type\":\"project.changed\",\"projectId\":\"" + projectId + "\",\"at\":\"" + clock.instant() + "\"}";
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(properties.webhookUrl()))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("X-WorkHub-Signature", "sha256=" + sign(payload))
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
			int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			if (status < 300) {
				return true;
			}
			log.warn("Integration webhook answered {}", status);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (Exception ex) {
			log.warn("Integration webhook delivery failed: {}", ex.toString());
		}
		return false;
	}

	private String sign(String payload) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}

}
