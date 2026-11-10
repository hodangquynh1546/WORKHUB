package com.workhub.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.workhub.issue.IssueStatus;

/** One column of a project's board. TO_DO, IN_PROGRESS and DONE are always enabled. */
public record WorkflowEntry(@NotNull IssueStatus status, @NotBlank @Size(max = 40) String name, boolean enabled) {
}
