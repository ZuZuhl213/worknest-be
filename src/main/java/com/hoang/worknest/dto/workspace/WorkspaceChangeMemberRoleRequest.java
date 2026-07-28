package com.hoang.worknest.dto.workspace;

import com.hoang.worknest.enums.WorkspaceRole;

import jakarta.validation.constraints.NotNull;

public record WorkspaceChangeMemberRoleRequest(@NotNull WorkspaceRole role) {
}
