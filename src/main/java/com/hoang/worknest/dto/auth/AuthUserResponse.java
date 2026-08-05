package com.hoang.worknest.dto.auth;

import com.hoang.worknest.enums.SystemRole;

public record AuthUserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl,
    Boolean emailVerified,
    SystemRole systemRole,
    Boolean canCreateWorkspace
) {
}
