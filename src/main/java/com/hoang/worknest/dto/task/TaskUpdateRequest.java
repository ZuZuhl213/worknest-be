package com.hoang.worknest.dto.task;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotNull TaskStatus status,
    @NotNull TaskPriority priority,
    Long assigneeUserId,
    OffsetDateTime dueDate
) {
}
