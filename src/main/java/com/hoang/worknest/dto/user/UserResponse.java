package com.hoang.worknest.dto.user;

import java.time.OffsetDateTime;
import com.hoang.worknest.enums.SystemRole;

public record UserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl,
    Boolean isActive,
    Boolean emailVerified,
    SystemRole systemRole,
    OffsetDateTime deactivatedAt,
    OffsetDateTime lastLoginAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
