package com.workhub.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.workhub.auth.AuthProperties;

class ProductionGuardTests {

	private static final String REAL_SECRET = "k3Jx9mQ2vL7pR5tY8wZ1cN4bV6hG0sDfAaBbCcDd";

	private static ProductionGuard guard(String secret, boolean secureCookie, String frontend) {
		return new ProductionGuard(new JwtProperties("workhub", secret, Duration.ofMinutes(15)),
				new AuthProperties(Duration.ofDays(14), Duration.ofHours(24), Duration.ofHours(1), secureCookie, frontend));
	}

	@Test
	void acceptsAProperConfiguration() {
		assertThatCode(() -> guard(REAL_SECRET, true, "https://workhub.example.com").check()).doesNotThrowAnyException();
	}

	@Test
	void refusesTheDevelopmentSecret() {
		assertThatThrownBy(() -> guard("dev-only-insecure-secret-change-me-0123456789", true, "https://x.example").check())
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void refusesAnInsecureCookieOrPlainHttp() {
		assertThatThrownBy(() -> guard(REAL_SECRET, false, "https://x.example").check())
			.hasMessageContaining("REFRESH_COOKIE_SECURE");
		assertThatThrownBy(() -> guard(REAL_SECRET, true, "http://x.example").check())
			.hasMessageContaining("FRONTEND_BASE_URL");
	}

}
