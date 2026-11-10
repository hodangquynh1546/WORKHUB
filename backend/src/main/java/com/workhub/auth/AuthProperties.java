package com.workhub.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workhub.auth")
public record AuthProperties(Duration refreshTokenTtl, Duration emailVerificationTtl, Duration passwordResetTtl,
		boolean refreshCookieSecure, String frontendBaseUrl) {
}
