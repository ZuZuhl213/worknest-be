package com.hoang.worknest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hoang.worknest.dto.workspace.WorkspaceMemberResponse;
import com.hoang.worknest.entity.WorkspaceMember;

@Mapper(componentModel = "spring")
public interface WorkspaceMemberMapper {

    @Mapping(target = "workspaceId", source = "workspace.id")
    WorkspaceMemberResponse toResponse(WorkspaceMember workspaceMember);
}
