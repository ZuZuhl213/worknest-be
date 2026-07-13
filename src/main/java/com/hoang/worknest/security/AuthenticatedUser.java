package com.hoang.worknest.security;

import java.time.OffsetDateTime;

public record AuthenticatedUser(
    Long id,
    String email,
    String fullName,
    Boolean emailVerified,
    Boolean isActive,
    OffsetDateTime lastLoginAt
) {
}
