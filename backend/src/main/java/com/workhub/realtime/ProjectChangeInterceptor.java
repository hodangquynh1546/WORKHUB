package com.workhub.realtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * After any successful write under the project-related API paths, tells everyone subscribed to that project that
 * something changed ({@code {"type":"project.changed","projectId":…,"area":"issues"}}). Clients reload the area they
 * show; the event carries no data, so there is nothing to keep consistent and no way to leak content.
 */
@Component
class ProjectChangeInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(ProjectChangeInterceptor.class);

	/** /api/projects/{id}/{area}… */
	private static final Pattern IN_PROJECT = Pattern.compile("^/api/projects/([0-9a-f-]{36})(?:/([a-z-]+))?.*$");

	/** /api/{resource}/{id}… for records that belong to a project. */
	private static final Pattern BY_RECORD = Pattern.compile("^/api/(issues|sprints|documents|document-folders|meetings|meeting-action-items)/([0-9a-f-]{36}).*$");

	private static final Map<String, String> TABLE = Map.of("issues", "issues", "sprints", "sprints", "documents", "documents",
			"document-folders", "document_folders", "meetings", "meetings", "meeting-action-items", "meeting_action_items");

	private static final Map<String, String> AREA = Map.of("issues", "issues", "sprints", "sprints", "documents", "documents",
			"document-folders", "documents", "meetings", "meetings", "meeting-action-items", "meetings");

	private final RealtimeHub hub;

	private final ProjectLookup lookup;

	private final com.workhub.integration.IntegrationOutbox outbox;

	ProjectChangeInterceptor(RealtimeHub hub, ProjectLookup lookup, com.workhub.integration.IntegrationOutbox outbox) {
		this.hub = hub;
		this.lookup = lookup;
		this.outbox = outbox;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		String method = request.getMethod();
		if (ex != null || response.getStatus() >= 300 || "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
			return;
		}
		try {
			String path = request.getRequestURI();
			Matcher inProject = IN_PROJECT.matcher(path);
			UUID projectId;
			String area;
			if (inProject.matches()) {
				projectId = UUID.fromString(inProject.group(1));
				area = areaOfSegment(inProject.group(2));
			}
			else {
				Matcher byRecord = BY_RECORD.matcher(path);
				if (!byRecord.matches()) {
					return;
				}
				Optional<UUID> found = lookup.projectOf(TABLE.get(byRecord.group(1)), UUID.fromString(byRecord.group(2)));
				if (found.isEmpty()) {
					return;
				}
				projectId = found.get();
				area = AREA.get(byRecord.group(1));
			}
			hub.sendToProject(projectId, Map.of("type", "project.changed", "projectId", projectId, "area", area, "actorId", actor()));
			outbox.projectChanged(projectId); // durable notification for integrations (no-op unless the project is linked)
		}
		catch (RuntimeException failure) {
			// Realtime is a convenience; a failure here must never turn a successful write into an error.
			log.debug("Could not publish project change", failure);
		}
	}

	private static String areaOfSegment(String segment) {
		if (segment == null) {
			return "project";
		}
		return switch (segment) {
			case "issues", "backlog", "board" -> "issues";
			case "sprints" -> "sprints";
			case "documents", "document-folders" -> "documents";
			case "meetings" -> "meetings";
			case "members" -> "members";
			default -> "project";
		};
	}

	private static String actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return (auth != null && auth.getPrincipal() instanceof Jwt jwt) ? jwt.getSubject() : "";
	}

	@Configuration
	static class Registration implements WebMvcConfigurer {

		private final ProjectChangeInterceptor interceptor;

		Registration(ProjectChangeInterceptor interceptor) {
			this.interceptor = interceptor;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(interceptor).addPathPatterns("/api/**");
		}

	}

}
