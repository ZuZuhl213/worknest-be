package com.hoang.worknest.dto.workspace;

import com.hoang.worknest.enums.Role;

import jakarta.validation.constraints.NotNull;

public record WorkspaceChangeMemberRoleRequest(@NotNull Role role) {
}
