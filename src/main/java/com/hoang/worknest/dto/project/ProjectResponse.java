package com.hoang.worknest.dto.project;

import java.time.OffsetDateTime;

public record ProjectResponse(
    Long id,
    Long workspaceId,
    String name,
    String projectKey,
    String description,
    Boolean archived,
    ProjectCreatorResponse createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
