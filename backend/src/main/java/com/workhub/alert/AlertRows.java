package com.workhub.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class AlertRows {

	private AlertRows() {
	}

	record IssueRow(UUID id, int number, String title, LocalDate dueDate, UUID assigneeId, Instant updatedAt) {
	}

}
