package com.workhub.export;

import java.util.List;

/**
 * A rectangular result ready to be written as CSV or Excel. Cells are {@code String}, {@code Number},
 * {@code Boolean} or {@code null}; anything else is written with {@code toString()} by the writers.
 */
public record Table(String title, List<String> headers, List<List<Object>> rows) {

	public static Table of(String title, List<String> headers, List<List<Object>> rows) {
		return new Table(title, headers, rows);
	}

}
