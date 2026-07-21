package com.hoang.worknest.dto.project;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.ProjectRole;

public record ProjectMemberResponse(
    Long id,
    Long projectId,
    ProjectMemberUserResponse user,
    ProjectRole role,
    ProjectMemberUserResponse addedBy,
    OffsetDateTime joinedAt,
    OffsetDateTime createdAt
) {}
