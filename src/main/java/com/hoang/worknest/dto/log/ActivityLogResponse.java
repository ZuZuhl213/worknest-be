package com.hoang.worknest.dto.log;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.hoang.worknest.dto.user.UserResponse;

public record ActivityLogResponse(
    Long id,
    Long workspaceId,
    Long projectId,
    Long taskId,
    UserResponse actor,
    String action,
    String entityType,
    Long entityId,
    JsonNode details,
    OffsetDateTime createdAt
) {
}
