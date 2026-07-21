package com.hoang.worknest.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
    @NotBlank @Size(max = 120) String fullName
) {
}
