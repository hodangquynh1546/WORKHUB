package com.workhub.common;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deleting a workspace or team removes its projects through ON DELETE CASCADE, but a few foreign keys between rows
 * that are deleted together have no cascade of their own (sprint history pointing at issues, issues pointing at
 * their sprint or parent). PostgreSQL refuses the delete while those links exist, so they are cut first, in the same
 * transaction. Native SQL on purpose: soft-deleted issues must be included.
 */
@Component
public class CascadeCleaner {

	private final JdbcTemplate jdbc;

	CascadeCleaner(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void beforeDeletingWorkspace(UUID workspaceId) {
		cut("select id from projects where workspace_id = ?", workspaceId);
	}

	public void beforeDeletingTeam(UUID teamId) {
		cut("select id from projects where team_id = ?", teamId);
	}

	private void cut(String projectIds, UUID scopeId) {
		String issues = "select id from issues where project_id in (" + projectIds + ")";
		jdbc.update("delete from sprint_issues where issue_id in (" + issues + ")", scopeId);
		jdbc.update("update issues set sprint_id = null, parent_id = null where project_id in (" + projectIds + ")", scopeId);
	}

}
