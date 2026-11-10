package com.workhub.project;

import java.time.LocalDate;

/** The editable part of a project, already normalised and validated by {@link ProjectService}. */
record ProjectDetails(String name, String description, String avatarUrl, LocalDate startDate, LocalDate endDate,
		Methodology methodology) {
}
