package com.hoang.worknest.dto.task;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;

public record WorkspaceTaskResponse(
    Long id,
    Long projectId,
    String projectName,
    String projectKey,
    Long taskNumber,
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    TaskUserResponse assignee,
    TaskUserResponse reporter,
    OffsetDateTime dueDate,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
