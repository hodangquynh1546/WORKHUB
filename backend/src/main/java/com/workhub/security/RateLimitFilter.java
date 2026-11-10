package com.workhub.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window limiter per client address for the endpoints an attacker can hammer without a token (sign-in, sign-up,
 * password reset, e-mail verification). The counters live in memory, so with several instances each one counts on its
 * own; put a limit at the reverse proxy as well when scaling out. {@code /api/auth/refresh} is not limited because
 * the SPA calls it on its own.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> LIMITED = Set.of("/api/auth/login", "/api/auth/register",
			"/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email");

	private final RateLimitProperties properties;

	private final Clock clock;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	RateLimitFilter(RateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled() || !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long now = clock.millis();
		long windowMillis = properties.window().toMillis();
		if (windows.size() > 10_000) {
			windows.values().removeIf((w) -> now - w.start >= windowMillis);
		}
		// The address is the one the container resolved (X-Forwarded-For only when server.forward-headers-strategy trusts it).
		Window window = windows.compute(request.getRemoteAddr() + " " + request.getRequestURI(),
				(key, current) -> (current == null || now - current.start >= windowMillis) ? new Window(now, 1)
						: new Window(current.start, current.count + 1));
		if (window.count > properties.authRequests()) {
			long retryAfter = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
			response.setStatus(429);
			response.setHeader("Retry-After", String.valueOf(retryAfter));
			response.setContentType("application/problem+json");
			response.getWriter()
				.write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
						+ "\"detail\":\"Too many attempts, try again later\",\"code\":\"RATE_LIMITED\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	private record Window(long start, int count) {
	}

}
