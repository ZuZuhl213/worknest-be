package com.hoang.worknest.dto.workspace;

public record WorkspaceOwnerResponse(
    Long id,
    String email,
    String fullName
) {
}
