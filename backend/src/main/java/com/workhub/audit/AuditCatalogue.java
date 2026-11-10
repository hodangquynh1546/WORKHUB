package com.workhub.audit;

import static com.workhub.audit.AuditRule.of;

import java.util.List;
import java.util.regex.Matcher;

/**
 * The actions that are audited: changes to who may do what, to the structure of workspaces/teams/projects, to
 * sprints, deletions, data exports and sign-in events. High-volume everyday work (comments, chat, ranking, status
 * moves) is deliberately left out: the issue history and the chat already hold it, and an audit log that records
 * everything cannot be read. Add a rule here to audit another endpoint.
 */
final class AuditCatalogue {

	record Match(AuditRule rule, Matcher groups) {
	}

	private static final List<AuditRule> RULES = List.of(
			// ---- accounts ----
			of("POST", "/api/auth/register", "auth.register").actorByEmail(),
			of("POST", "/api/auth/login", "auth.login").actorByEmail().failures(401).failureAction("auth.login_failed"),
			of("POST", "/api/me/password", "auth.password_changed"),

			// ---- workspaces and invitations ----
			of("POST", "/api/workspaces", "workspace.create").created("WORKSPACE").scopeFromResponse(),
			// The workspace is gone afterwards, so the row has no workspace scope (it stays in the actor's own trail) and the name comes from the typed confirmation.
			of("DELETE", "/api/workspaces/{workspace}", "workspace.delete").target("WORKSPACE", "workspace").detail("name"),
			of("PUT", "/api/workspaces/{workspace}", "workspace.update").target("WORKSPACE", "workspace").scope("workspaces", "workspace"),
			of("PUT", "/api/workspaces/{workspace}/members/{user}", "workspace.member_role_change").target("USER", "user")
				.scope("workspaces", "workspace").detail("role"),
			of("DELETE", "/api/workspaces/{workspace}/members/{user}", "workspace.member_remove").target("USER", "user")
				.scope("workspaces", "workspace"),
			of("POST", "/api/workspaces/{workspace}/invitations", "invitation.create").target("WORKSPACE", "workspace")
				.scope("workspaces", "workspace").detail("kind", "role", "email"),
			of("POST", "/api/teams/{team}/invitations", "invitation.create").target("TEAM", "team").scope("teams", "team")
				.detail("kind", "role", "email"),
			of("POST", "/api/projects/{project}/invitations", "invitation.create").target("PROJECT", "project").scope("projects", "project")
				.detail("kind", "role", "email"),
			of("DELETE", "/api/invitations/{invitation}", "invitation.revoke").scope("invitations", "invitation"),
			of("POST", "/api/invitations/accept", "invitation.accept").target("WORKSPACE", null).scopeFromResponse(),
			of("POST", "/api/me/invitations/{invitation}/accept", "invitation.accept").target("WORKSPACE", null).scope("invitations", "invitation"),

			// ---- teams ----
			of("POST", "/api/workspaces/{workspace}/teams", "team.create").created("TEAM").scope("workspaces", "workspace"),
			of("DELETE", "/api/teams/{team}", "team.delete").target("TEAM", "team").scope("teams", "team").detail("name"),
			of("PUT", "/api/teams/{team}", "team.update").target("TEAM", "team").scope("teams", "team"),
			of("POST", "/api/teams/{team}/archive", "team.archive").target("TEAM", "team").scope("teams", "team"),
			of("POST", "/api/teams/{team}/unarchive", "team.unarchive").target("TEAM", "team").scope("teams", "team"),
			of("POST", "/api/teams/{team}/members", "team.member_add").target("TEAM", "team").scope("teams", "team").detail("userId", "role"),
			of("PUT", "/api/teams/{team}/members/{user}", "team.member_role_change").target("USER", "user").scope("teams", "team").detail("role"),
			of("DELETE", "/api/teams/{team}/members/{user}", "team.member_remove").target("USER", "user").scope("teams", "team"),

			// ---- projects ----
			of("POST", "/api/teams/{team}/projects", "project.create").created("PROJECT").scope("teams", "team"),
			of("PUT", "/api/projects/{project}", "project.update").target("PROJECT", "project").scope("projects", "project").detail("methodology"),
			of("PUT", "/api/projects/{project}/status", "project.status_change").target("PROJECT", "project").scope("projects", "project").detail("status"),
			of("POST", "/api/projects/{project}/members", "project.member_add").target("PROJECT", "project").scope("projects", "project").detail("userId", "role"),
			of("PUT", "/api/projects/{project}/members/{user}", "project.member_role_change").target("USER", "user").scope("projects", "project").detail("role"),
			of("DELETE", "/api/projects/{project}/members/{user}", "project.member_remove").target("USER", "user").scope("projects", "project"),
			of("PUT", "/api/projects/{project}/alert-settings", "project.alert_settings").target("PROJECT", "project").scope("projects", "project"),
			of("PUT", "/api/projects/{project}/workflow", "project.workflow_change").target("PROJECT", "project").scope("projects", "project"),

			// ---- sprints ----
			of("POST", "/api/projects/{project}/sprints", "sprint.create").created("SPRINT").scope("projects", "project"),
			of("PUT", "/api/sprints/{sprint}", "sprint.update").target("SPRINT", "sprint").scope("sprints", "sprint"),
			of("POST", "/api/sprints/{sprint}/start", "sprint.start").target("SPRINT", "sprint").scope("sprints", "sprint"),
			of("POST", "/api/sprints/{sprint}/complete", "sprint.complete").target("SPRINT", "sprint").scope("sprints", "sprint").detail("goalOutcome", "unfinishedTo"),
			of("POST", "/api/sprints/{sprint}/cancel", "sprint.cancel").target("SPRINT", "sprint").scope("sprints", "sprint"),
			of("POST", "/api/sprints/{sprint}/issues", "sprint.scope_add").target("SPRINT", "sprint").scope("sprints", "sprint"),
			of("DELETE", "/api/sprints/{sprint}/issues/{issue}", "sprint.scope_remove").target("SPRINT", "sprint").scope("sprints", "sprint"),

			// ---- deletions and sensitive document/meeting actions ----
			of("DELETE", "/api/issues/{issue}", "issue.delete").target("ISSUE", "issue").scope("issues", "issue"),
			of("DELETE", "/api/documents/{document}", "document.delete").target("DOCUMENT", "document").scope("documents", "document"),
			of("POST", "/api/documents/{document}/archive", "document.archive").target("DOCUMENT", "document").scope("documents", "document"),
			of("POST", "/api/documents/{document}/unarchive", "document.unarchive").target("DOCUMENT", "document").scope("documents", "document"),
			of("POST", "/api/documents/{document}/versions/{#version}/restore", "document.version_restore").target("DOCUMENT", "document").scope("documents", "document"),
			of("DELETE", "/api/meetings/{meeting}", "meeting.delete").target("MEETING", "meeting").scope("meetings", "meeting"),
			of("POST", "/api/meetings/{meeting}/cancel", "meeting.cancel").target("MEETING", "meeting").scope("meetings", "meeting"),

			// ---- data leaving the system ----
			of("GET", "/api/projects/{project}/export/{*what}", "export.project").target("PROJECT", "project").scope("projects", "project").detail("format"),
			of("GET", "/api/sprints/{sprint}/export", "export.sprint").target("SPRINT", "sprint").scope("sprints", "sprint").detail("format"),
			of("GET", "/api/sprints/{sprint}/export/{*what}", "export.sprint").target("SPRINT", "sprint").scope("sprints", "sprint").detail("format"),
			of("GET", "/api/teams/{team}/export/{*what}", "export.team").target("TEAM", "team").scope("teams", "team").detail("format"),
			of("GET", "/api/workspaces/{workspace}/export/{*what}", "export.workspace").target("WORKSPACE", "workspace").scope("workspaces", "workspace").detail("format"),
			of("GET", "/api/workspaces/{workspace}/audit-log/export", "export.audit_log").target("WORKSPACE", "workspace").scope("workspaces", "workspace").detail("format"));

	private AuditCatalogue() {
	}

	/** The rule for this request, with its path groups, or null when it is not audited. */
	static Match find(String method, String uri) {
		for (AuditRule rule : RULES) {
			Matcher m = rule.match(method, uri);
			if (m != null) {
				return new Match(rule, m);
			}
		}
		return null;
	}

}
