package com.workhub.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SprintRequests {

	private SprintRequests() {
	}

	/**
	 * A sprint lasts at most one month. {@code capacityPoints} is what the team expects to be able to take on and is
	 * only used to compare with the commitment.
	 */
	public record Save(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String goal,
			@NotNull LocalDate startDate, @NotNull LocalDate endDate, UUID facilitatorId,
			@Min(0) @Max(10000) Integer capacityPoints) {
	}

	/** {@code includeChildren} also takes every unfinished descendant (sub-tasks, a feature's stories...). */
	public record AddIssues(@NotEmpty @Size(max = 200) List<@NotNull UUID> issueIds, boolean includeChildren) {
	}

	public enum UnfinishedTo { BACKLOG, NEXT_SPRINT }

	/** Where unfinished issues go; for NEXT_SPRINT, {@code nextSprintId} must be a FUTURE sprint of the project. */
	public record Complete(@NotNull GoalOutcome goalOutcome, @Size(max = 1000) String goalNote,
			@NotNull UnfinishedTo unfinishedTo, UUID nextSprintId) {
	}

}
