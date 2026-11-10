package com.workhub.issue;

/**
 * Fixed workflow for now (a configurable per-project workflow arrives with the board). Only DONE counts as done:
 * an issue is never marked done just because a sprint ended.
 */
public enum IssueStatus {

	TO_DO(Category.TO_DO), IN_PROGRESS(Category.IN_PROGRESS), CODE_REVIEW(Category.IN_PROGRESS),
	TESTING(Category.IN_PROGRESS), DONE(Category.DONE);

	public enum Category { TO_DO, IN_PROGRESS, DONE }

	private final Category category;

	IssueStatus(Category category) {
		this.category = category;
	}

	public Category category() {
		return category;
	}

}
