package com.hoang.worknest.dto.workspace;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.Role;

public record WorkspaceMemberResponse(
    Long id,
    Long workspaceId,
    WorkspaceMemberUserResponse user,
    Role role,
    WorkspaceMemberUserResponse invitedBy,
    OffsetDateTime joinedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
