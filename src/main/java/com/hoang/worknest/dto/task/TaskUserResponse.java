package com.hoang.worknest.dto.task;

public record TaskUserResponse(
    Long id,
    String email,
    String fullName
) {
}
