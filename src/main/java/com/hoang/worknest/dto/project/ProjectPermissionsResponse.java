package com.hoang.worknest.dto.project;

public record ProjectPermissionsResponse(
    boolean canViewProject,
    boolean canCreateTask,
    boolean canAssignTask,
    boolean canComment,
    boolean canManageProject,
    boolean canManageMembers
) {
}
