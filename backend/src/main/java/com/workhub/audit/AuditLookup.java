package com.workhub.audit;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.workhub.common.Uuids;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Plain-SQL lookups for the recorder: soft-deleted rows must still resolve, so entities are not used. */
@Component
@Transactional(readOnly = true)
class AuditLookup {

	record Scope(UUID workspaceId, UUID projectId) {
	}

	record Person(UUID id, String name) {
	}

	private final EntityManager em;

	AuditLookup(EntityManager em) {
		this.em = em;
	}

	/** Workspace (and project, where the row has one) a row of {@code table} belongs to. */
	Scope scope(String table, UUID id) {
		String sql = switch (table) {
			case "workspaces" -> "select id, cast(null as uuid) from workspaces where id = :id";
			case "teams" -> "select workspace_id, cast(null as uuid) from teams where id = :id";
			case "projects" -> "select workspace_id, id from projects where id = :id";
			case "issues" -> "select p.workspace_id, p.id from issues i join projects p on p.id = i.project_id where i.id = :id";
			case "sprints" -> "select p.workspace_id, p.id from sprints s join projects p on p.id = s.project_id where s.id = :id";
			case "documents" -> "select p.workspace_id, p.id from documents d join projects p on p.id = d.project_id where d.id = :id";
			case "meetings" -> "select p.workspace_id, p.id from meetings m join projects p on p.id = m.project_id where m.id = :id";
			case "invitations" -> "select workspace_id, cast(null as uuid) from invitations where id = :id";
			default -> throw new IllegalArgumentException(table);
		};
		List<?> rows = em.createNativeQuery(sql).setParameter("id", id).getResultList();
		if (rows.isEmpty()) {
			return new Scope(null, null);
		}
		Object[] row = (Object[]) rows.get(0);
		return new Scope(Uuids.from(row[0]), Uuids.from(row[1]));
	}

	/** A readable name for the target, or null. */
	String label(String targetType, UUID id) {
		String sql = switch (targetType) {
			case "WORKSPACE" -> "select name from workspaces where id = :id";
			case "TEAM" -> "select name from teams where id = :id";
			case "PROJECT" -> "select name from projects where id = :id";
			case "ISSUE" -> "select p.project_key || '-' || i.issue_number || ' ' || i.title from issues i join projects p on p.id = i.project_id where i.id = :id";
			case "SPRINT" -> "select name from sprints where id = :id";
			case "DOCUMENT" -> "select title from documents where id = :id";
			case "MEETING" -> "select title from meetings where id = :id";
			case "USER" -> "select full_name from users where id = :id";
			default -> null;
		};
		if (sql == null) {
			return null;
		}
		List<?> rows = em.createNativeQuery(sql).setParameter("id", id).getResultList();
		return rows.isEmpty() ? null : String.valueOf(rows.get(0));
	}

	Person personById(UUID id) {
		List<?> rows = em.createNativeQuery("select full_name from users where id = :id").setParameter("id", id).getResultList();
		return rows.isEmpty() ? null : new Person(id, String.valueOf(rows.get(0)));
	}

	Person personByEmail(String email) {
		List<?> rows = em.createNativeQuery("select id, full_name from users where email = :email")
			.setParameter("email", email.strip().toLowerCase(java.util.Locale.ROOT))
			.getResultList();
		if (rows.isEmpty()) {
			return null;
		}
		Object[] row = (Object[]) rows.get(0);
		return new Person(Uuids.from(row[0]), String.valueOf(row[1]));
	}

}
