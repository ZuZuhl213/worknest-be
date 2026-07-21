package com.hoang.worknest.dto.project;

import com.hoang.worknest.enums.ProjectRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProjectAddMemberRequest(
    @NotBlank @Email String email,
    @NotNull ProjectRole role
) {}
