package com.workhub.sprint;

/** Aggregates over the issues currently in a sprint. Points are story points (issues without any count as 0). */
public record SprintCounts(int issues, int todo, int inProgress, int done, int scopePoints, int donePoints) {

	static final SprintCounts EMPTY = new SprintCounts(0, 0, 0, 0, 0, 0);

	public int remainingPoints() {
		return scopePoints - donePoints;
	}

}
