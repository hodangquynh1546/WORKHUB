package com.workhub.realtime;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the project a record belongs to with plain SQL, so soft-deleted rows (which the entities hide) still
 * resolve: when an issue is deleted, its project's viewers must still hear about it.
 */
@Component
@Transactional(readOnly = true)
class ProjectLookup {

	private final EntityManager em;

	ProjectLookup(EntityManager em) {
		this.em = em;
	}

	Optional<UUID> projectOf(String table, UUID id) {
		String sql = switch (table) {
			case "issues" -> "select project_id from issues where id = :id";
			case "sprints" -> "select project_id from sprints where id = :id";
			case "documents" -> "select project_id from documents where id = :id";
			case "document_folders" -> "select project_id from document_folders where id = :id";
			case "meetings" -> "select project_id from meetings where id = :id";
			case "meeting_action_items" -> "select m.project_id from meetings m join meeting_action_items a on a.meeting_id = m.id where a.id = :id";
			default -> throw new IllegalArgumentException(table);
		};
		var rows = em.createNativeQuery(sql).setParameter("id", id).getResultList();
		return rows.isEmpty() ? Optional.empty() : Optional.of(com.workhub.common.Uuids.from(rows.get(0)));
	}

}
