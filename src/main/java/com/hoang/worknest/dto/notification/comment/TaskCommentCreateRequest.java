package com.hoang.worknest.dto.notification.comment;

import jakarta.validation.constraints.NotBlank;

public record TaskCommentCreateRequest(@NotBlank String content) {
}
