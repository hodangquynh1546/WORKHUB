package com.workhub.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class Uuids {

	private Uuids() {
	}

	/** Native queries return a UUID column as UUID (PostgreSQL), 16 bytes (H2) or text, depending on the driver. */
	public static UUID from(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof UUID id) {
			return id;
		}
		if (value instanceof byte[] bytes && bytes.length == 16) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			return new UUID(buffer.getLong(), buffer.getLong());
		}
		return UUID.fromString(String.valueOf(value));
	}

}
