package com.hoang.worknest.security;

import java.time.OffsetDateTime;
import com.hoang.worknest.enums.SystemRole;

public record AuthenticatedUser(
    Long id,
    String email,
    String fullName,
    Boolean emailVerified,
    Boolean isActive,
    SystemRole systemRole,
    Boolean canCreateWorkspace,
    Integer tokenVersion,
    OffsetDateTime lastLoginAt
) {
}
