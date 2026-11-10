package com.workhub.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

import jakarta.servlet.http.HttpServletRequest;

import com.workhub.audit.AuditLookup.Person;
import com.workhub.audit.AuditLookup.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a matched request into an {@link AuditEntry}: who (token, or the account named by the body's email for
 * sign-in/up), what (the rule), on what (path ids or the response id), where (the workspace and project the row
 * belongs to) and a few safe details. It never throws into the request: an audit failure is logged and the
 * user's action stands.
 */
@Component
class AuditRecorder {

	private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

	private static final int MAX_BODY = 16 * 1024;

	private final AuditRepository entries;

	private final AuditLookup lookup;

	private final JsonMapper json;

	AuditRecorder(AuditRepository entries, AuditLookup lookup, JsonMapper json) {
		this.entries = entries;
		this.lookup = lookup;
		this.json = json;
	}

	@Transactional
	public void record(AuditCatalogue.Match match, HttpServletRequest request, int status, byte[] requestBody, byte[] responseBody) {
		try {
			AuditRule rule = match.rule();
			boolean ok = status >= 200 && status < 300;
			boolean failed = rule.failures.contains(status);
			if (!ok && !failed) {
				return;
			}
			JsonNode body = parse(requestBody);
			JsonNode result = ok ? parse(responseBody) : null;

			Person actor = actor(rule, body);
			if (actor == null) {
				return;
			}
			Matcher groups = match.groups();
			UUID targetId = rule.targetFromResponse ? id(result, "id") : (rule.targetGroup != null ? uuid(groups, rule.targetGroup) : null);
			Scope scope = scope(rule, groups, result);
			if (targetId == null && "WORKSPACE".equals(rule.targetType)) {
				targetId = scope.workspaceId();
			}
			UUID projectId = scope.projectId();
			if ("PROJECT".equals(rule.targetType) && targetId != null && projectId == null) {
				projectId = targetId;
			}
			String label = (rule.targetType != null && targetId != null) ? lookup.label(rule.targetType, targetId) : null;
			entries.save(new AuditEntry(scope.workspaceId(), projectId, actor.id(), actor.name(), (failed && rule.failureAction != null) ? rule.failureAction : rule.action, rule.targetType, targetId,
					label, detail(rule, body, request), failed ? "FAILED" : "SUCCESS", request.getRemoteAddr()));
		}
		catch (RuntimeException ex) {
			log.warn("Could not write audit entry for {} {}", request.getMethod(), request.getRequestURI(), ex);
		}
	}

	private Person actor(AuditRule rule, JsonNode body) {
		if (rule.actorByEmail) {
			String email = (body == null) ? "" : body.path("email").asString("");
			// Attempts for addresses nobody has registered are not logged: anyone could fill the table with them.
			return email.isBlank() ? null : lookup.personByEmail(email);
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			try {
				return lookup.personById(UUID.fromString(jwt.getSubject()));
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
		}
		return null;
	}

	private Scope scope(AuditRule rule, Matcher groups, JsonNode result) {
		if (rule.scopeFromResponse) {
			UUID workspace = (result == null) ? null : id(result, result.has("workspaceId") ? "workspaceId" : "id");
			return new Scope(workspace, null);
		}
		if (rule.scopeTable != null) {
			UUID id = uuid(groups, rule.scopeGroup);
			if (id != null) {
				return lookup.scope(rule.scopeTable, id);
			}
		}
		return new Scope(null, null);
	}

	private String detail(AuditRule rule, JsonNode body, HttpServletRequest request) {
		List<String> parts = new ArrayList<>();
		for (String key : rule.detailKeys) {
			String value = null;
			if (body != null && body.has(key) && body.get(key).isValueNode() && !body.get(key).isNull()) {
				value = body.get(key).asString("");
			}
			else if (request.getParameter(key) != null) {
				value = request.getParameter(key);
			}
			if (value == null || value.isBlank()) {
				continue;
			}
			if ("userId".equals(key)) {
				try {
					Person person = lookup.personById(UUID.fromString(value));
					parts.add("member=" + ((person == null) ? value : person.name()));
				}
				catch (IllegalArgumentException ex) {
					// not an id: leave it out
				}
				continue;
			}
			parts.add(key + "=" + (value.length() > 80 ? value.substring(0, 80) : value));
		}
		return parts.isEmpty() ? null : String.join("; ", parts);
	}

	private JsonNode parse(byte[] bytes) {
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_BODY) {
			return null;
		}
		try {
			JsonNode node = json.readTree(bytes);
			return node.isObject() ? node : null;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static UUID id(JsonNode node, String field) {
		if (node == null || !node.has(field)) {
			return null;
		}
		try {
			return UUID.fromString(node.get(field).asString(""));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static UUID uuid(Matcher groups, String name) {
		try {
			return UUID.fromString(groups.group(name));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return null;
		}
	}

}
