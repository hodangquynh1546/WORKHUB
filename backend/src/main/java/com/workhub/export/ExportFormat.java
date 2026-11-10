package com.workhub.export;

import org.springframework.http.MediaType;

public enum ExportFormat {

	CSV("csv", "text/csv;charset=UTF-8"),
	XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final String extension;

	private final MediaType mediaType;

	ExportFormat(String extension, String mediaType) {
		this.extension = extension;
		this.mediaType = MediaType.parseMediaType(mediaType);
	}

	public String extension() {
		return extension;
	}

	public MediaType mediaType() {
		return mediaType;
	}

	/** Parses the {@code format} request parameter; anything unknown is a 400 for the caller to raise. */
	public static ExportFormat parse(String value) {
		for (ExportFormat f : values()) {
			if (f.extension.equalsIgnoreCase(value)) {
				return f;
			}
		}
		throw new com.workhub.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_FORMAT",
				"Format must be csv or xlsx");
	}

}
