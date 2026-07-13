package com.hoang.worknest.dto.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String title,
    String content,
    Boolean read,
    LocalDateTime createdAt
) {
}
