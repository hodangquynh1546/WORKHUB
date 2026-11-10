package com.workhub.meeting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Meeting list filters, all optional. {@code from}/{@code to} select meetings overlapping that window;
 * {@code personId} keeps meetings that person organizes or attends.
 */
record MeetingFilter(Instant from, Instant to, MeetingType type, UUID sprintId, UUID personId,
		List<MeetingStatus> statuses) {

	Specification<Meeting> toSpecification(UUID projectId) {
		return (root, query, cb) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(cb.equal(root.get("projectId"), projectId));
			if (from != null) {
				all.add(cb.greaterThan(root.get("endsAt"), from));
			}
			if (to != null) {
				all.add(cb.lessThan(root.get("startsAt"), to));
			}
			if (type != null) {
				all.add(cb.equal(root.get("type"), type));
			}
			if (sprintId != null) {
				all.add(cb.equal(root.get("sprintId"), sprintId));
			}
			if (statuses != null && !statuses.isEmpty()) {
				all.add(root.get("status").in(statuses));
			}
			if (personId != null) {
				all.add(cb.or(cb.equal(root.get("organizerId"), personId), attends(root, query.subquery(Long.class), cb)));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	private Predicate attends(Root<Meeting> root, Subquery<Long> sub, CriteriaBuilder cb) {
		Root<MeetingAttendee> attendee = sub.from(MeetingAttendee.class);
		sub.select(cb.literal(1L))
			.where(cb.equal(attendee.get("meetingId"), root.get("id")), cb.equal(attendee.get("userId"), personId));
		return cb.exists(sub);
	}

}
