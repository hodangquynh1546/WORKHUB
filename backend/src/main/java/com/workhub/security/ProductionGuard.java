package com.workhub.security;

import jakarta.annotation.PostConstruct;

import com.workhub.auth.AuthProperties;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fails the start of the {@code prod} profile when a development default is still in place. */
@Component
@Profile("prod")
class ProductionGuard {

	private final JwtProperties jwt;

	private final AuthProperties auth;

	ProductionGuard(JwtProperties jwt, AuthProperties auth) {
		this.jwt = jwt;
		this.auth = auth;
	}

	@PostConstruct
	void check() {
		if (jwt.secret().startsWith("dev-only-insecure-secret")) {
			throw new IllegalStateException("JWT_SECRET is not set: refusing to run the prod profile with the built-in development secret");
		}
		if (!auth.refreshCookieSecure()) {
			throw new IllegalStateException("REFRESH_COOKIE_SECURE must be true in production (serve the app over HTTPS)");
		}
		if (!auth.frontendBaseUrl().startsWith("https://")) {
			throw new IllegalStateException("FRONTEND_BASE_URL must be an https:// URL in production");
		}
	}

}
