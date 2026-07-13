package com.hoang.worknest.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceUpdateRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 100) String slug,
    String description,
    Boolean archived
) {
}
