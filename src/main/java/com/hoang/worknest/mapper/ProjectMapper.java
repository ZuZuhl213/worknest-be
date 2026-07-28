package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.hoang.worknest.dto.project.ProjectCreateRequest;
import com.hoang.worknest.dto.project.ProjectPermissionsResponse;
import com.hoang.worknest.dto.project.ProjectResponse;
import com.hoang.worknest.dto.project.ProjectUpdateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.enums.ProjectRole;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    @Mapping(target = "workspaceId", source = "project.workspace.id")
    @Mapping(target = "myRole", source = "myRole")
    @Mapping(target = "permissions", source = "permissions")
    ProjectResponse toResponse(
        Project project,
        ProjectRole myRole,
        ProjectPermissionsResponse permissions
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "archived", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    Project toEntity(ProjectCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    void updateEntity(ProjectUpdateRequest request, @MappingTarget Project project);
}
