package com.hoang.worknest.dto.workspace;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.WorkspaceRole;

public record WorkspaceMemberResponse(
    Long id,
    Long workspaceId,
    WorkspaceMemberUserResponse user,
    WorkspaceRole role,
    WorkspaceMemberUserResponse invitedBy,
    OffsetDateTime joinedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
