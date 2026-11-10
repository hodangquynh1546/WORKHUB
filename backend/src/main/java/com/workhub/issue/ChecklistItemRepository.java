package com.workhub.issue;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

	List<ChecklistItem> findAllByIssueIdOrderByPositionAscCreatedAtAsc(UUID issueId);

	long countByIssueId(UUID issueId);

}
