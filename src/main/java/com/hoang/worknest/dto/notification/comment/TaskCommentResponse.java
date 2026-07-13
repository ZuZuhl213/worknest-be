package com.hoang.worknest.dto.notification.comment;

import java.time.OffsetDateTime;

public record TaskCommentResponse(
    Long id,
    Long taskId,
    TaskCommentAuthorResponse author,
    String content,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
