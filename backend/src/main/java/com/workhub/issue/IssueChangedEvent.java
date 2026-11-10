package com.workhub.issue;

import java.util.UUID;

/**
 * Published (synchronously, inside the changing transaction) when an issue that belongs to a sprint changes in a way
 * that moves the sprint's numbers: status, story points or type.
 */
public record IssueChangedEvent(UUID issueId, UUID sprintId) {
}
