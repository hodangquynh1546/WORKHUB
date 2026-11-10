package com.workhub.sprint;

public enum RemovalReason {

	/** Taken out of the sprint by hand. */
	REMOVED,
	/** Moved straight into another sprint. */
	MOVED,
	/** Not done when the sprint completed; went back to the backlog or into the next sprint. */
	CARRIED_OVER,
	/** The sprint was cancelled. */
	CANCELLED

}
