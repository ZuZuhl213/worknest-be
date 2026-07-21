package com.hoang.worknest.mapper;

import org.springframework.stereotype.Component;

import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.entity.User;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getIsActive(),
            user.getEmailVerified(),
            user.getSystemRole(),
            user.getDeactivatedAt(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
