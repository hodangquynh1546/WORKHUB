package com.workhub.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workhub.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl) {

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("workhub.jwt.secret must be at least 32 bytes for HS256");
		}
	}

}
