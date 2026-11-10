package com.workhub.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token lives in an HttpOnly cookie scoped to {@code /api/auth}, so page scripts cannot read it
 * and it is only sent to the refresh/logout endpoints. SameSite=Strict keeps other sites from triggering them.
 */
@Component
public class RefreshCookies {

	public static final String NAME = "workhub_refresh";

	private final AuthProperties properties;

	RefreshCookies(AuthProperties properties) {
		this.properties = properties;
	}

	public ResponseCookie issue(String refreshToken) {
		return build(refreshToken, properties.refreshTokenTtl());
	}

	public ResponseCookie clear() {
		return build("", Duration.ZERO);
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(properties.refreshCookieSecure())
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(maxAge)
			.build();
	}

}
