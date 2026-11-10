package com.workhub.audit;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Says which requests are worth an audit line and how to describe them. {@code template} is a path where
 * {@code {name}} matches an id (UUID), {@code {#name}} a number and {@code {*name}} a word; the groups are named
 * after it. Only successful requests are recorded, unless {@link #failures} lists status codes to record as FAILED.
 */
final class AuditRule {

	final String method;

	final Pattern pattern;

	final String action;

	/** Target taken from a path group (type + group name), or from the response id for creations. */
	String targetType;

	String targetGroup;

	boolean targetFromResponse;

	/** The row whose workspace/project the entry belongs to: table + path group. */
	String scopeTable;

	String scopeGroup;

	/** Creations: the new record's id and workspace come from the response body. */
	boolean scopeFromResponse;

	/** Request-body keys worth keeping (small enum-like fields only; never free text or secrets). */
	List<String> detailKeys = List.of();

	/** Statuses to record as FAILED (e.g. 401 on login). */
	Set<Integer> failures = Set.of();

	/** Action recorded instead of {@link #action} when the request failed with one of {@link #failures}. */
	String failureAction;

	/** The actor is the account named by the body's {@code email} (sign-up and sign-in have no token). */
	boolean actorByEmail;

	private AuditRule(String method, String template, String action) {
		this.method = method;
		this.pattern = Pattern.compile("^" + template
			.replaceAll("\\{#(\\w+)}", "(?<$1>[0-9]+)")
			.replaceAll("\\{\\*(\\w+)}", "(?<$1>[a-z-]+)")
			.replaceAll("\\{(\\w+)}", "(?<$1>[0-9a-fA-F-]{36})") + "/?$");
		this.action = action;
	}

	static AuditRule of(String method, String template, String action) {
		return new AuditRule(method, template, action);
	}

	AuditRule target(String type, String group) {
		this.targetType = type;
		this.targetGroup = group;
		return this;
	}

	AuditRule created(String type) {
		this.targetType = type;
		this.targetFromResponse = true;
		return this;
	}

	AuditRule scope(String table, String group) {
		this.scopeTable = table;
		this.scopeGroup = group;
		return this;
	}

	AuditRule scopeFromResponse() {
		this.scopeFromResponse = true;
		return this;
	}

	AuditRule detail(String... keys) {
		this.detailKeys = List.of(keys);
		return this;
	}

	AuditRule failures(Integer... statuses) {
		this.failures = Set.of(statuses);
		return this;
	}

	AuditRule failureAction(String action) {
		this.failureAction = action;
		return this;
	}

	AuditRule actorByEmail() {
		this.actorByEmail = true;
		return this;
	}

	Matcher match(String httpMethod, String uri) {
		if (!method.equals(httpMethod)) {
			return null;
		}
		Matcher m = pattern.matcher(uri);
		return m.matches() ? m : null;
	}

}
