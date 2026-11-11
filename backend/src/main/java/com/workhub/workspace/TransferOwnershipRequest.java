package com.workhub.workspace;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The new owner (an existing member) and the current owner's password as a second confirmation. */
public record TransferOwnershipRequest(@NotNull UUID userId, @NotBlank String password) {
}
