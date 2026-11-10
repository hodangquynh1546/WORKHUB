package com.workhub.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Brute-force guard for the public auth endpoints: at most {@code authRequests} per client address per {@code window}. */
@ConfigurationProperties("workhub.rate-limit")
public record RateLimitProperties(boolean enabled, int authRequests, Duration window) {

	public RateLimitProperties {
		if (authRequests < 1) {
			throw new IllegalArgumentException("workhub.rate-limit.auth-requests must be at least 1");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("workhub.rate-limit.window must be positive");
		}
	}

}
