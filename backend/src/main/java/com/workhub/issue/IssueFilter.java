package com.workhub.issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Backlog filters. Every field is optional; lists mean "any of". {@code epicId} matches issues under that epic at any
 * depth (up to epic → feature → story → sub-task).
 */
public record IssueFilter(String q, List<IssueType> types, List<IssueStatus> statuses,
		List<IssuePriority> priorities, UUID assigneeId, boolean unassigned, UUID epicId, String label,
		RefinementStatus refinement, UUID sprintId, boolean unplanned) {

	private static final Pattern KEY_OR_NUMBER = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9]*-)?(\\d+)$");

	Specification<Issue> toSpecification(UUID projectId) {
		return (root, query, cb) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(cb.equal(root.get("projectId"), projectId));
			if (q != null && !q.isBlank()) {
				String text = q.strip();
				Predicate title = cb.like(cb.lower(root.get("title")),
						"%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%", '\\');
				Matcher key = KEY_OR_NUMBER.matcher(text);
				all.add(key.matches() ? cb.or(title, cb.equal(root.get("number"), Integer.parseInt(key.group(1))))
						: title);
			}
			if (types != null && !types.isEmpty()) {
				all.add(root.get("type").in(types));
			}
			if (statuses != null && !statuses.isEmpty()) {
				all.add(root.get("status").in(statuses));
			}
			if (priorities != null && !priorities.isEmpty()) {
				all.add(root.get("priority").in(priorities));
			}
			if (assigneeId != null) {
				all.add(cb.equal(root.get("assigneeId"), assigneeId));
			}
			else if (unassigned) {
				all.add(cb.isNull(root.get("assigneeId")));
			}
			if (sprintId != null) {
				all.add(cb.equal(root.get("sprintId"), sprintId));
			}
			else if (unplanned) {
				all.add(cb.isNull(root.get("sprintId")));
			}
			if (refinement != null) {
				all.add(cb.equal(root.get("refinement"), refinement));
			}
			if (label != null && !label.isBlank()) {
				all.add(cb.isMember(label.strip().toLowerCase(Locale.ROOT), root.<java.util.Set<String>>get("labels")));
			}
			if (epicId != null) {
				all.add(cb.or(cb.equal(root.get("parentId"), epicId), underEpic(root, query.subquery(Long.class), cb)));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	/** Grandchildren and deeper: a parent whose own parent (or grandparent) is the epic. */
	private Predicate underEpic(Root<Issue> root, Subquery<Long> sub, jakarta.persistence.criteria.CriteriaBuilder cb) {
		Root<Issue> parent = sub.from(Issue.class);
		Root<Issue> grand = sub.from(Issue.class);
		sub.select(cb.literal(1L))
			.where(cb.equal(parent.get("id"), root.get("parentId")), cb.equal(grand.get("id"), parent.get("parentId")),
					cb.or(cb.equal(grand.get("id"), epicId), cb.equal(grand.get("parentId"), epicId)));
		return cb.exists(sub);
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
