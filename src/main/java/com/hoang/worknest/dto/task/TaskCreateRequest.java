package com.hoang.worknest.dto.task;

import java.time.OffsetDateTime;

import com.hoang.worknest.enums.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskCreateRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotNull TaskPriority priority,
    Long assigneeUserId,
    OffsetDateTime dueDate
) {
}
