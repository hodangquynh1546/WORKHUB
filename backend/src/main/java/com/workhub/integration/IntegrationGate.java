package com.workhub.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Tells whether the current request comes from the integration itself (ForgeBridge) rather than from a person: it
 * carries {@code X-Integration-Key} equal to {@code workhub.integration.webhook-secret}. Content owned by another
 * system is read-only for everyone else. With no secret configured the lock is off (local development).
 */
@Component
public class IntegrationGate {

	static final String HEADER = "X-Integration-Key";

	private final IntegrationProperties properties;

	IntegrationGate(IntegrationProperties properties) {
		this.properties = properties;
	}

	public boolean lockEnabled() {
		return !properties.webhookSecret().isEmpty();
	}

	/** True when content managed by another system may be changed by this request. */
	public boolean mayChangeManaged() {
		return !lockEnabled() || trusted();
	}

	public boolean trusted() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servlet)) {
			return false;
		}
		HttpServletRequest request = servlet.getRequest();
		String given = request.getHeader(HEADER);
		return lockEnabled() && given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
				properties.webhookSecret().getBytes(StandardCharsets.UTF_8));
	}

}
