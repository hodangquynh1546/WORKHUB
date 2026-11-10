package com.workhub.export;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Turns a {@link Table} into a download response. */
public final class Exports {

	private Exports() {
	}

	public static byte[] bytes(Table table, ExportFormat format) {
		return (format == ExportFormat.CSV) ? CsvWriter.write(table) : XlsxWriter.write(table);
	}

	/** The attachment, named {@code workhub-<name>-<date>.<ext>}. */
	public static ResponseEntity<byte[]> download(Table table, ExportFormat format, String name, LocalDate today) {
		String file = "workhub-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "") + "-" + today
				+ "." + format.extension();
		return ResponseEntity.ok()
			.contentType(format.mediaType())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(bytes(table, format));
	}

}
