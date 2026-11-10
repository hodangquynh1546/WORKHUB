package com.workhub.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A minimal single-sheet .xlsx (Office Open XML) writer with no dependencies: bold header row, frozen at the top,
 * column widths from the content, numbers as numbers and text as inline strings. Enough for data hand-offs; it
 * deliberately has no formulas, styles per cell or dates (dates are written as ISO text).
 */
final class XlsxWriter {

	private static final int MAX_WIDTH = 60;

	private XlsxWriter() {
	}

	static byte[] write(Table table) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			put(zip, "[Content_Types].xml", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
					<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
					<Default Extension="xml" ContentType="application/xml"/>
					<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
					<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
					<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
					</Types>""");
			put(zip, "_rels/.rels", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
					<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
					</Relationships>""");
			put(zip, "xl/workbook.xml", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
					<sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
					</workbook>""".formatted(escape(sheetName(table.title()))));
			put(zip, "xl/_rels/workbook.xml.rels", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
					<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
					<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
					</Relationships>""");
			put(zip, "xl/styles.xml", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
					<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
					<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
					<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
					<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
					<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
					<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
					</styleSheet>""");
			put(zip, "xl/worksheets/sheet1.xml", sheet(table));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

	private static String sheet(Table table) {
		StringBuilder xml = new StringBuilder("""
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
				<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
				<cols>""");
		for (int c = 0; c < table.headers().size(); c++) {
			xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
				.append(width(table, c)).append("\" customWidth=\"1\"/>");
		}
		xml.append("</cols><sheetData>");
		xml.append("<row r=\"1\">");
		for (int c = 0; c < table.headers().size(); c++) {
			xml.append(text(ref(c, 1), table.headers().get(c), 1));
		}
		xml.append("</row>");
		int r = 2;
		for (List<Object> row : table.rows()) {
			xml.append("<row r=\"").append(r).append("\">");
			for (int c = 0; c < row.size(); c++) {
				Object value = row.get(c);
				if (value == null) {
					continue;
				}
				if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
					xml.append("<c r=\"").append(ref(c, r)).append("\"><v>").append(n).append("</v></c>");
				}
				else if (value instanceof Boolean b) {
					xml.append("<c r=\"").append(ref(c, r)).append("\" t=\"b\"><v>").append(b ? 1 : 0).append("</v></c>");
				}
				else {
					xml.append(text(ref(c, r), CsvWriter.defuse(value.toString()), 0));
				}
			}
			xml.append("</row>");
			r++;
		}
		return xml.append("</sheetData></worksheet>").toString();
	}

	private static String text(String ref, String value, int style) {
		return "<c r=\"" + ref + "\" t=\"inlineStr\"" + ((style > 0) ? " s=\"" + style + "\"" : "") + "><is><t xml:space=\"preserve\">"
				+ escape(value) + "</t></is></c>";
	}

	private static int width(Table table, int column) {
		int max = table.headers().get(column).length();
		for (List<Object> row : table.rows().subList(0, Math.min(200, table.rows().size()))) {
			if (column < row.size() && row.get(column) != null) {
				max = Math.max(max, row.get(column).toString().length());
			}
		}
		return Math.min(MAX_WIDTH, Math.max(8, max + 2));
	}

	/** Column index (0-based) and row (1-based) to a cell reference such as {@code AB12}. */
	static String ref(int column, int row) {
		StringBuilder letters = new StringBuilder();
		for (int c = column; c >= 0; c = c / 26 - 1) {
			letters.insert(0, (char) ('A' + c % 26));
		}
		return letters.toString() + row;
	}

	private static String sheetName(String title) {
		String clean = (title == null || title.isBlank()) ? "Sheet1" : title.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
		return clean.length() > 31 ? clean.substring(0, 31) : clean;
	}

	/** XML 1.0 forbids most control characters; they are dropped, the rest is escaped. */
	private static String escape(String text) {
		StringBuilder out = new StringBuilder(text.length());
		text.codePoints().forEach((cp) -> {
			if (cp == '\t' || cp == '\n' || cp == '\r' || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || cp >= 0x10000) {
				switch (cp) {
					case '&' -> out.append("&amp;");
					case '<' -> out.append("&lt;");
					case '>' -> out.append("&gt;");
					case '"' -> out.append("&quot;");
					default -> out.appendCodePoint(cp);
				}
			}
		});
		return out.toString();
	}

	private static void put(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.stripLeading().getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}
