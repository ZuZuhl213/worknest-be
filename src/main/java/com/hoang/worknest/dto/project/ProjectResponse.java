package com.hoang.worknest.dto.project;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.ProjectRole;

public record ProjectResponse(
    Long id,
    Long workspaceId,
    String name,
    String projectKey,
    String description,
    Boolean archived,
    ProjectRole myRole,
    ProjectPermissionsResponse permissions,
    ProjectCreatorResponse createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
