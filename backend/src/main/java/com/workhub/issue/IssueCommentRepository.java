package com.workhub.issue;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

	List<IssueComment> findAllByIssueIdOrderByCreatedAtAscIdAsc(UUID issueId);

}
