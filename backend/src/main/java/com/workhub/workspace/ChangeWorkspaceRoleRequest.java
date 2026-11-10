package com.workhub.workspace;

import jakarta.validation.constraints.NotNull;

public record ChangeWorkspaceRoleRequest(@NotNull WorkspaceRole role) {
}
