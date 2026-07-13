package com.hoang.worknest.dto.workspace;

public record WorkspaceMemberUserResponse(
    Long id,
    String email,
    String fullName
) {
}
