package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.hoang.worknest.dto.task.TaskCreateRequest;
import com.hoang.worknest.dto.task.TaskResponse;
import com.hoang.worknest.dto.task.TaskUpdateRequest;
import com.hoang.worknest.entity.Task;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "projectId", source = "project.id")
    TaskResponse toResponse(Task task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "taskNumber", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    Task toEntity(TaskCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "taskNumber", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    void updateEntity(TaskUpdateRequest request, @MappingTarget Task task);
}
