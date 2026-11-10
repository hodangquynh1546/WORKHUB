package com.workhub.issue;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface IssueActivityRepository extends JpaRepository<IssueActivity, UUID> {

	List<IssueActivity> findByIssueIdOrderByCreatedAtDescIdDesc(UUID issueId, Pageable pageable);

}
