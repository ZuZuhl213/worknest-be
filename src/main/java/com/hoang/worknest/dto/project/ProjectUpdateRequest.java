package com.hoang.worknest.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 20) String projectKey,
    String description,
    Boolean archived
) {
}
