package com.hoang.worknest.dto.project;

public record ProjectMemberUserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl
) {}
