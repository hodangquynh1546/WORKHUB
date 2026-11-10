package com.workhub.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV as Excel and LibreOffice expect it: UTF-8 with a byte-order mark (so Vietnamese letters survive), CRLF line
 * ends, quotes only where needed. Text that a spreadsheet would run as a formula is defused.
 */
final class CsvWriter {

	private CsvWriter() {
	}

	static byte[] write(Table table) {
		StringBuilder out = new StringBuilder("﻿");
		line(out, table.headers().stream().map((h) -> (Object) h).toList());
		for (List<Object> row : table.rows()) {
			line(out, row);
		}
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void line(StringBuilder out, List<Object> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(cell(cells.get(i)));
		}
		out.append("\r\n");
	}

	private static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = (value instanceof Number || value instanceof Boolean) ? String.valueOf(value) : defuse(value.toString());
		boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
		return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
	}

	/** A text cell starting with = + - @ (or a control character) would be evaluated as a formula by a spreadsheet. */
	static String defuse(String text) {
		if (!text.isEmpty()) {
			char first = text.charAt(0);
			if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
				return "'" + text;
			}
		}
		return text;
	}

}
