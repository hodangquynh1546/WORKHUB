package com.workhub.report;

import java.time.Instant;
import java.util.UUID;

/** Lightweight projections for the report queries (no entities, so no lazy loading). */
final class ReportRows {

	private ReportRows() {
	}

	record DoneRow(UUID id, int number, String title, Instant createdAt, Instant startedAt, Instant doneAt) {
	}

	record BugRow(Instant createdAt, Instant doneAt) {
	}

}
