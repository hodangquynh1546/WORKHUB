package com.workhub.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.Issue;
import com.workhub.report.ReportRows.BugRow;
import com.workhub.report.ReportRows.DoneRow;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregate queries over issues for the reports (soft-deleted issues are excluded by {@code Issue}). */
interface ReportRepository extends Repository<Issue, UUID> {

	@Query("""
			select new com.workhub.report.ReportRows$DoneRow(i.id, i.number, i.title, i.createdAt, i.startedAt, i.doneAt)
			from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status = com.workhub.issue.IssueStatus.DONE and i.doneAt >= :since
			order by i.doneAt
			""")
	List<DoneRow> doneSince(@Param("projectId") UUID projectId, @Param("since") Instant since);

	/** Rows: assignee id (may be null), status, issue count, story points. Open (not done) issues, epics excluded. */
	@Query("""
			select i.assigneeId, i.status, count(i), coalesce(sum(i.storyPoints), 0) from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.status <> com.workhub.issue.IssueStatus.DONE
			group by i.assigneeId, i.status
			""")
	List<Object[]> openWorkload(@Param("projectId") UUID projectId);

	@Query("""
			select new com.workhub.report.ReportRows$BugRow(i.createdAt, i.doneAt) from Issue i
			where i.projectId = :projectId and i.type = com.workhub.issue.IssueType.BUG
			""")
	List<BugRow> bugs(@Param("projectId") UUID projectId);

}
