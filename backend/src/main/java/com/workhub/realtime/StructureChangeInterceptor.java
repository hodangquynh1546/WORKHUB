package com.workhub.realtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Workspaces, teams and projects (create, rename, archive, status, picture, members, delete) show up in lists on
 * every member's screen, so after a successful write the workspace's people hear {@code structure.changed} and
 * reload what they show. The audience is read <em>before</em> the handler runs, so a deletion (which cascades the
 * membership rows away) and someone being removed still reach the people affected. The frame carries no data.
 */
@Component
class StructureChangeInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(StructureChangeInterceptor.class);

	private static final String ATTRIBUTE = StructureChangeInterceptor.class.getName() + ".audience";

	private static final Pattern WORKSPACE = Pattern.compile("^/api/workspaces/([0-9a-f-]{36})(?:/.*)?$");

	private static final Pattern TEAM = Pattern.compile("^/api/teams/([0-9a-f-]{36})(?:/.*)?$");

	/** Only the project's own record, status, picture and members: issues, sprints, ... are not structure. */
	private static final Pattern PROJECT = Pattern.compile("^/api/projects/([0-9a-f-]{36})(?:/(?:status|avatar|members|invitations)(?:/.*)?)?$");

	private record Audience(UUID workspaceId, Set<UUID> people) {
	}

	private final RealtimeHub hub;

	private final StructureLookup lookup;

	StructureChangeInterceptor(RealtimeHub hub, StructureLookup lookup) {
		this.hub = hub;
		this.lookup = lookup;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (isRead(request.getMethod())) {
			return true;
		}
		try {
			String path = request.getRequestURI();
			Optional<UUID> workspace = Optional.empty();
			Matcher ws = WORKSPACE.matcher(path);
			Matcher team = TEAM.matcher(path);
			Matcher project = PROJECT.matcher(path);
			if (ws.matches()) {
				workspace = lookup.workspaceOf("workspaces", UUID.fromString(ws.group(1)));
			}
			else if (team.matches()) {
				workspace = lookup.workspaceOf("teams", UUID.fromString(team.group(1)));
			}
			else if (project.matches()) {
				workspace = lookup.workspaceOf("projects", UUID.fromString(project.group(1)));
			}
			Set<UUID> people = new HashSet<>();
			workspace.ifPresent((id) -> people.addAll(lookup.peopleOf(id)));
			request.setAttribute(ATTRIBUTE, new Audience(workspace.orElse(null), people));
		}
		catch (RuntimeException failure) {
			log.debug("Could not work out who to tell about a structure change", failure);
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		if (ex != null || response.getStatus() >= 300 || isRead(request.getMethod())) {
			return;
		}
		try {
			Audience audience = (Audience) request.getAttribute(ATTRIBUTE);
			boolean createsWorkspace = "POST".equals(request.getMethod()) && "/api/workspaces".equals(request.getRequestURI());
			if (audience == null && !createsWorkspace) {
				return;
			}
			Set<UUID> people = (audience != null) ? new HashSet<>(audience.people()) : new HashSet<>();
			UUID actor = actor();
			if (actor != null) {
				people.add(actor);
			}
			hub.sendToUsers(people, Map.of("type", "structure.changed",
					"workspaceId", (audience != null && audience.workspaceId() != null) ? audience.workspaceId().toString() : "",
					"actorId", (actor != null) ? actor.toString() : ""));
		}
		catch (RuntimeException failure) {
			log.debug("Could not publish structure change", failure);
		}
	}

	private static boolean isRead(String method) {
		return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
	}

	private static UUID actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			try {
				return UUID.fromString(jwt.getSubject());
			}
			catch (IllegalArgumentException ignored) {
				return null;
			}
		}
		return null;
	}

	@Configuration
	static class Registration implements WebMvcConfigurer {

		private final StructureChangeInterceptor interceptor;

		Registration(StructureChangeInterceptor interceptor) {
			this.interceptor = interceptor;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(interceptor).addPathPatterns("/api/workspaces", "/api/workspaces/**", "/api/teams/**", "/api/projects/**");
		}

	}

}
