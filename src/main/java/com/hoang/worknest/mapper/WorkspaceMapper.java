package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hoang.worknest.dto.workspace.WorkspaceCreateRequest;
import com.hoang.worknest.dto.workspace.WorkspaceResponse;
import com.hoang.worknest.dto.workspace.WorkspaceUpdateRequest;
import com.hoang.worknest.entity.Workspace;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    @Mapping(target = "archived", source = "archived")
    WorkspaceResponse toResponse(Workspace workspace);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "archived", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    Workspace toEntity(WorkspaceCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "activityLogs", ignore = true)
    void updateEntity(WorkspaceUpdateRequest request, @org.mapstruct.MappingTarget Workspace workspace);
}
