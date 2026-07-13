package com.hoang.worknest.dto.task;

import jakarta.validation.constraints.NotNull;

public record TaskAssignRequest(
    @NotNull Long assigneeUserId
) {
}
