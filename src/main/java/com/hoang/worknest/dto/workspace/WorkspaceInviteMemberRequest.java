package com.hoang.worknest.dto.workspace;

import com.hoang.worknest.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkspaceInviteMemberRequest(
    @NotBlank @Email String email,
    @NotNull Role role
) {
}
