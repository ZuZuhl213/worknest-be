package com.hoang.worknest.dto.auth;

import java.time.OffsetDateTime;

public record AuthResponse(
    String tokenType,
    String accessToken,
    OffsetDateTime accessTokenExpiresAt,
    AuthUserResponse user
) {
}
