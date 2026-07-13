package com.hoang.worknest.dto.workspace;

import java.time.OffsetDateTime;

public record WorkspaceResponse(
    Long id,
    String name,
    String slug,
    String description,
    Boolean archived,
    WorkspaceOwnerResponse owner,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
