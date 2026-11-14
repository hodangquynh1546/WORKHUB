package com.workhub.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Single-use, 90-second sign-in tickets: another system of the same account (SmartSpec, through ForgeBridge) opens
 * WorkHub already signed in as the same person. A ticket is {@code userId.expiryMillis.hmac}; the HMAC key is the
 * integration secret, so only a holder of it can mint one. Redeemed tickets are remembered until they expire.
 */
@Component
public class SsoTickets {

	static final long TTL_MILLIS = 90_000;

	private final IntegrationProperties properties;

	private final Clock clock;

	private final ConcurrentHashMap<String, Long> used = new ConcurrentHashMap<>();

	SsoTickets(IntegrationProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public String issue(UUID userId) {
		String body = userId + "." + (clock.millis() + TTL_MILLIS);
		return body + "." + sign(body);
	}

	/** The user the ticket was issued for, once; empty when forged, expired or already used. */
	public Optional<UUID> redeem(String ticket) {
		if (ticket == null || properties.webhookSecret().isEmpty()) {
			return Optional.empty();
		}
		String[] parts = ticket.split("\\.");
		if (parts.length != 3) {
			return Optional.empty();
		}
		String body = parts[0] + "." + parts[1];
		if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
			return Optional.empty();
		}
		long now = clock.millis();
		long expiry;
		UUID userId;
		try {
			expiry = Long.parseLong(parts[1]);
			userId = UUID.fromString(parts[0]);
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		used.values().removeIf(e -> e < now);
		if (expiry < now || used.putIfAbsent(ticket, expiry) != null) {
			return Optional.empty();
		}
		return Optional.of(userId);
	}

	private String sign(String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(("sso:" + properties.webhookSecret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
