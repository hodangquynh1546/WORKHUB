package com.workhub.document;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DocumentRequests {

	/** Longest document text accepted (characters). */
	public static final int MAX_CONTENT = 100_000;

	private DocumentRequests() {
	}

	public record Create(@NotBlank @Size(max = 200) String title, @NotNull DocumentType type, UUID folderId,
			@Size(max = MAX_CONTENT) String content) {
	}

	/** {@code baseVersion} is the version the editor started from; a newer one on the server means a conflict. */
	public record Update(@NotBlank @Size(max = 200) String title, @NotNull DocumentType type, UUID folderId,
			@NotNull @Size(max = MAX_CONTENT) String content, @NotNull Integer baseVersion,
			@Size(max = 200) String note) {
	}

	public record SaveFolder(@NotBlank @Size(max = 100) String name, UUID parentId) {
	}

	public record AddLink(@NotNull LinkTargetType targetType, @NotNull UUID targetId) {
	}

}
