package com.workhub.board;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.workhub.issue.IssueStatus;

interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, WorkflowStatusKey> {

	/** Rows: status, name, enabled. */
	@Query("select w.key.status, w.name, w.enabled from WorkflowStatus w where w.key.projectId = :projectId")
	List<Object[]> findRows(@Param("projectId") UUID projectId);

}
