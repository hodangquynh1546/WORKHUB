package com.workhub.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for creating or updating a workspace. */
public record WorkspaceRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
		@Size(max = 1024) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String logoUrl) {
}
