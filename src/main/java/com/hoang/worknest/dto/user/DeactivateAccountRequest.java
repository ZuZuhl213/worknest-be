package com.hoang.worknest.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateAccountRequest(
    @NotBlank @Size(max = 128) String currentPassword
) {
}
