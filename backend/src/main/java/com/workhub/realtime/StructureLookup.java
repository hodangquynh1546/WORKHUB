package com.workhub.realtime;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.workhub.common.Uuids;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Plain-SQL lookups for {@link StructureChangeInterceptor}: it must still work right before a row is deleted. */
@Component
@Transactional(readOnly = true)
class StructureLookup {

	private final EntityManager em;

	StructureLookup(EntityManager em) {
		this.em = em;
	}

	Optional<UUID> workspaceOf(String table, UUID id) {
		String sql = switch (table) {
			case "workspaces" -> "select id from workspaces where id = :id";
			case "teams" -> "select workspace_id from teams where id = :id";
			case "projects" -> "select workspace_id from projects where id = :id";
			default -> throw new IllegalArgumentException(table);
		};
		var rows = em.createNativeQuery(sql).setParameter("id", id).getResultList();
		return rows.isEmpty() ? Optional.empty() : Optional.of(Uuids.from(rows.get(0)));
	}

	/** Everyone with a membership row in the workspace, guests included: they all see some part of its structure. */
	Set<UUID> peopleOf(UUID workspaceId) {
		Set<UUID> ids = new HashSet<>();
		for (Object row : em.createNativeQuery("select user_id from workspace_members where workspace_id = :id")
			.setParameter("id", workspaceId).getResultList()) {
			ids.add(Uuids.from(row));
		}
		return ids;
	}

}
