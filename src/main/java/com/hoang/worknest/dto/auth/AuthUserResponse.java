package com.hoang.worknest.dto.auth;

public record AuthUserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl,
    Boolean emailVerified
) {
}
