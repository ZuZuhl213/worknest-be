package com.hoang.worknest.dto.log;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.hoang.worknest.dto.user.UserResponse;

public record SecurityAuditLogResponse(
    Long id,
    UserResponse actor,
    UserResponse target,
    String action,
    String outcome,
    String ipAddress,
    String userAgent,
    JsonNode metadata,
    OffsetDateTime createdAt
) {
}
