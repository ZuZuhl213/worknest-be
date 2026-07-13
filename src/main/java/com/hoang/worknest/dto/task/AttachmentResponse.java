package com.hoang.worknest.dto.task;

import java.time.OffsetDateTime;
import com.hoang.worknest.dto.user.UserResponse;

public record AttachmentResponse(
    Long id,
    String fileName,
    String contentType,
    Long fileSize,
    String url,
    UserResponse uploadedBy,
    OffsetDateTime createdAt
) {}
