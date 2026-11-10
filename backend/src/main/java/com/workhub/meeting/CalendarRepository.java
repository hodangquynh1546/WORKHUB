package com.workhub.meeting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workhub.issue.Issue;
import com.workhub.meeting.CalendarRows.DueRow;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Issue due dates for the calendar (epics are containers and have no deadline of their own). */
interface CalendarRepository extends Repository<Issue, UUID> {

	String SELECT = """
			select new com.workhub.meeting.CalendarRows$DueRow(i.id, i.number, i.title, i.dueDate, i.status, i.assigneeId)
			from Issue i
			where i.projectId = :projectId and i.type <> com.workhub.issue.IssueType.EPIC
				and i.dueDate between :from and :to
			""";

	@Query(SELECT + " order by i.dueDate, i.number")
	List<DueRow> dueBetween(@Param("projectId") UUID projectId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(SELECT + " and i.assigneeId = :assigneeId order by i.dueDate, i.number")
	List<DueRow> dueBetweenFor(@Param("projectId") UUID projectId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("assigneeId") UUID assigneeId);

}
