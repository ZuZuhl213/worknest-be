package com.hoang.worknest.dto.notification.comment;

public record TaskCommentAuthorResponse(
    Long id,
    String email,
    String fullName
) {
}
