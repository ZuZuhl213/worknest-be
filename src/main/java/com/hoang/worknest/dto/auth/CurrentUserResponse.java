package com.hoang.worknest.dto.auth;

import java.time.OffsetDateTime;
import com.hoang.worknest.enums.SystemRole;

public record CurrentUserResponse(
    Long id,
    String email,
    String fullName,
    Boolean emailVerified,
    Boolean isActive,
    SystemRole systemRole,
    OffsetDateTime lastLoginAt
) {
}
