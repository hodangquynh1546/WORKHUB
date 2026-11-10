package com.workhub.common;

public final class Strings {

	private Strings() {
	}

	/** Trims the value and returns {@code null} when nothing is left. */
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
