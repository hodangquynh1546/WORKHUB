package com.workhub.issue;

import java.util.Set;

/** The kinds of work item and where each may sit in the hierarchy (Epic > Feature > Story/Task/Bug/Spike > Sub-task). */
public enum IssueType {

	EPIC, FEATURE, USER_STORY, TASK, SUB_TASK, BUG, SPIKE;

	/** Types a parent of this type may have. Empty means the type is always top-level. */
	Set<IssueType> allowedParents() {
		return switch (this) {
			case EPIC -> Set.of();
			case FEATURE -> Set.of(EPIC);
			case USER_STORY, TASK, BUG, SPIKE -> Set.of(EPIC, FEATURE);
			case SUB_TASK -> Set.of(USER_STORY, TASK, BUG, SPIKE);
		};
	}

	boolean requiresParent() {
		return this == SUB_TASK;
	}

}
