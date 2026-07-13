package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hoang.worknest.dto.user.UserCreateRequest;
import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", source = "password")
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(UserCreateRequest request);
}
