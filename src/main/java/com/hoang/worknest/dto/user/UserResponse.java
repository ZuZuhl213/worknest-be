package com.hoang.worknest.dto.user;

import java.time.OffsetDateTime;

public record UserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl,
    Boolean isActive,
    Boolean emailVerified,
    OffsetDateTime lastLoginAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
