package com.workhub.audit;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Wraps only the requests the {@link AuditCatalogue} knows, so every other request is untouched. It runs after
 * Spring Security (default filter order), when the caller's token is already resolved, and records once the
 * controller has answered, so only what actually happened is logged.
 */
@Component
class AuditFilter extends OncePerRequestFilter {

	private static final int MAX_CACHED_BODY = 16 * 1024;

	private final AuditRecorder recorder;

	AuditFilter(AuditRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/") || AuditCatalogue.find(request.getMethod(), request.getRequestURI()) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		AuditCatalogue.Match match = AuditCatalogue.find(request.getMethod(), request.getRequestURI());
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_CACHED_BODY);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		try {
			chain.doFilter(wrappedRequest, wrappedResponse);
		}
		finally {
			recorder.record(match, wrappedRequest, wrappedResponse.getStatus(), wrappedRequest.getContentAsByteArray(),
					wrappedResponse.getContentAsByteArray());
			wrappedResponse.copyBodyToResponse();
		}
	}

}
