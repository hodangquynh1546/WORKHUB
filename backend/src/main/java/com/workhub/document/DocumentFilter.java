package com.workhub.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Document list filters; every field is optional. {@code q} matches title or text. Without {@code statuses} only
 * ACTIVE documents are listed. {@code folderId} means directly inside that folder, {@code unfiled} outside any.
 */
public record DocumentFilter(String q, List<DocumentType> types, List<DocumentStatus> statuses, UUID folderId,
		boolean unfiled, LinkTargetType linkedType, UUID linkedId) {

	Specification<Document> toSpecification(UUID projectId) {
		return (root, query, cb) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(cb.equal(root.get("projectId"), projectId));
			if (q != null && !q.isBlank()) {
				String like = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
				all.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
						cb.like(cb.lower(root.<String>get("content")), like, '\\')));
			}
			if (types != null && !types.isEmpty()) {
				all.add(root.get("type").in(types));
			}
			all.add(root.get("status").in((statuses == null || statuses.isEmpty()) ? List.of(DocumentStatus.ACTIVE) : statuses));
			if (folderId != null) {
				all.add(cb.equal(root.get("folderId"), folderId));
			}
			else if (unfiled) {
				all.add(cb.isNull(root.get("folderId")));
			}
			if (linkedType != null && linkedId != null) {
				all.add(linkedTo(root, query.subquery(Long.class), cb));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	private Predicate linkedTo(Root<Document> root, Subquery<Long> sub, jakarta.persistence.criteria.CriteriaBuilder cb) {
		Root<DocumentLink> link = sub.from(DocumentLink.class);
		sub.select(cb.literal(1L))
			.where(cb.equal(link.get("documentId"), root.get("id")), cb.equal(link.get("targetType"), linkedType),
					cb.equal(link.get("targetId"), linkedId));
		return cb.exists(sub);
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
