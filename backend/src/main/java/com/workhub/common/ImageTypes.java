package com.workhub.common;

import java.util.Set;

/** Pictures accepted for avatars. The declared type is client-supplied, so the file's magic bytes must agree. */
public final class ImageTypes {

	public static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private ImageTypes() {
	}

	public static boolean matches(String contentType, byte[] d) {
		return switch (contentType) {
			case "image/jpeg" -> d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
			case "image/png" -> d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
			case "image/webp" -> d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W'
					&& d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
			default -> false;
		};
	}

}
