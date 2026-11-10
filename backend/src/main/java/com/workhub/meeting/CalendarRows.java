package com.workhub.meeting;

import java.time.LocalDate;
import java.util.UUID;

import com.workhub.issue.IssueStatus;

final class CalendarRows {

	private CalendarRows() {
	}

	record DueRow(UUID id, int number, String title, LocalDate dueDate, IssueStatus status, UUID assigneeId) {
	}

}
