package com.workhub.sprint;

/** FUTURE → ACTIVE → COMPLETED, or CANCELLED from FUTURE/ACTIVE. FUTURE and ACTIVE sprints are "open". */
public enum SprintStatus {

	FUTURE, ACTIVE, COMPLETED, CANCELLED;

	public boolean isOpen() {
		return this == FUTURE || this == ACTIVE;
	}

}
