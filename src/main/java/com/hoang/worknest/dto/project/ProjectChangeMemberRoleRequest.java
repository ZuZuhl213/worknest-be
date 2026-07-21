package com.hoang.worknest.dto.project;

import com.hoang.worknest.enums.ProjectRole;

import jakarta.validation.constraints.NotNull;

public record ProjectChangeMemberRoleRequest(
    @NotNull ProjectRole role
) {}
