package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hoang.worknest.dto.notification.comment.TaskCommentResponse;
import com.hoang.worknest.entity.TaskComment;

@Mapper(componentModel = "spring")
public interface TaskCommentMapper {

    @Mapping(target = "taskId", source = "task.id")
    TaskCommentResponse toResponse(TaskComment comment);
}
