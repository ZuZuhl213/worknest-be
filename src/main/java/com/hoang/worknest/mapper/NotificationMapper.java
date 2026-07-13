package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;

import com.hoang.worknest.dto.notification.NotificationResponse;
import com.hoang.worknest.entity.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
