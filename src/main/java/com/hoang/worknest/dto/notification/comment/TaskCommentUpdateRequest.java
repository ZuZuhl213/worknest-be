package com.hoang.worknest.dto.notification.comment;

import jakarta.validation.constraints.NotBlank;

public record TaskCommentUpdateRequest(@NotBlank String content) {
}
