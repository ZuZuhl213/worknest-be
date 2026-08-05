package com.hoang.worknest.dto.user;

import java.util.List;

public record AdminOverviewResponse(
    long totalAccounts,
    long activeAccounts,
    long disabledAccounts,
    long emailVerifiedAccounts,
    List<UserResponse> recentUsers
) {
}
