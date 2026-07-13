package com.hoang.worknest.dto.auth;

import java.time.OffsetDateTime;

public record CurrentUserResponse(
    Long id,
    String email,
    String fullName,
    Boolean emailVerified,
    Boolean isActive,
    OffsetDateTime lastLoginAt
) {
}
