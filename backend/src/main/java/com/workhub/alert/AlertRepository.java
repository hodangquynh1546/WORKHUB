package com.workhub.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workhub.alert.AlertRows.IssueRow;
import com.workhub.issue.Issue;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Issue queries behind the progress alerts. Epics are containers and never raise alerts. */
interface AlertRepository extends Repository<Issue, UUID> {

	String ROW = "select new com.workhub.alert.AlertRows$IssueRow(i.id, i.number, i.title, i.dueDate, i.assigneeId, i.updatedAt) from Issue i";

	/** Not done and due on or before {@code to} (overdue ones included). */
	@Query(ROW + """
			 where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status <> com.workhub.issue.IssueStatus.DONE and i.dueDate <= :to
			 order by i.dueDate, i.number
			""")
	List<IssueRow> dueUntil(@Param("projectId") UUID projectId, @Param("to") LocalDate to);

	@Query(ROW + """
			 where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status in (com.workhub.issue.IssueStatus.IN_PROGRESS, com.workhub.issue.IssueStatus.CODE_REVIEW,
					com.workhub.issue.IssueStatus.TESTING)
				and i.updatedAt < :before
			 order by i.updatedAt
			""")
	List<IssueRow> staleInProgress(@Param("projectId") UUID projectId, @Param("before") Instant before);

	@Query(ROW + """
			 where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status <> com.workhub.issue.IssueStatus.DONE and i.blocked = true
			 order by i.number
			""")
	List<IssueRow> blocked(@Param("projectId") UUID projectId);

	/** Rows: assignee id, number of open issues. */
	@Query("""
			select i.assigneeId, count(i) from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status <> com.workhub.issue.IssueStatus.DONE and i.assigneeId is not null
			group by i.assigneeId
			""")
	List<Object[]> openByAssignee(@Param("projectId") UUID projectId);

}
