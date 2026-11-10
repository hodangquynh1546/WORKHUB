package com.workhub.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/**
 * Audit log filters, all optional. {@code action} matches exactly or, when it ends with a dot ("project."), a whole
 * family; {@code q} searches the actor, the target's name and the detail.
 */
public record AuditQuery(UUID actorId, String action, UUID projectId, String q, Instant from, Instant to, String outcome) {

	Specification<AuditEntry> toSpecification(Specification<AuditEntry> scope) {
		return (root, query, cb) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(scope.toPredicate(root, query, cb));
			if (actorId != null) {
				all.add(cb.equal(root.get("actorId"), actorId));
			}
			if (action != null && !action.isBlank()) {
				String a = action.strip();
				all.add(a.endsWith(".") ? cb.like(root.get("action"), a.replace("%", "") + "%") : cb.equal(root.get("action"), a));
			}
			if (projectId != null) {
				all.add(cb.equal(root.get("projectId"), projectId));
			}
			if (from != null) {
				all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				all.add(cb.lessThan(root.get("createdAt"), to));
			}
			if (outcome != null && !outcome.isBlank()) {
				all.add(cb.equal(root.get("outcome"), outcome.strip().toUpperCase(Locale.ROOT)));
			}
			if (q != null && !q.isBlank()) {
				String like = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				all.add(cb.or(cb.like(cb.lower(root.get("actorName")), like, '\\'), cb.like(cb.lower(root.get("targetLabel")), like, '\\'),
						cb.like(cb.lower(root.get("detail")), like, '\\')));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

}
