package com.workhub.common;

import jakarta.validation.constraints.NotBlank;

/** Body of the destructive DELETE endpoints: the caller must type the exact name of what is being deleted. */
public record DeleteConfirmation(@NotBlank String name) {

	public void require(String actualName) {
		if (!actualName.strip().equals(name.strip())) {
			throw ApiException.badRequest("NAME_MISMATCH", "The confirmation does not match the name");
		}
	}

}
