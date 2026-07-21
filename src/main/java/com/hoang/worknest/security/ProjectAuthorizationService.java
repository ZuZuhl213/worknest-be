package com.hoang.worknest.security;

import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.Role;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CurrentUserService currentUserService;

    public Project requireAccess(Long workspaceId, Long projectId) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        Access access = resolveAccess(workspaceId, projectId);
        if (!access.allowed()) {
            // Do not reveal the existence of a private project.
            throw new ResourceNotFoundException("Project not found");
        }
        return project;
    }

    public Project requireMember(Long workspaceId, Long projectId) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        Access access = resolveAccess(workspaceId, projectId);
        if (!access.allowed()) {
            throw new ResourceNotFoundException("Project not found");
        }
        if (!access.workspaceAdmin()
            && access.projectRole() != ProjectRole.LEAD
            && access.projectRole() != ProjectRole.MEMBER) {
            throw new ForbiddenException("Project viewers have read-only access");
        }
        return project;
    }

    public Project requireLead(Long workspaceId, Long projectId) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        Access access = resolveAccess(workspaceId, projectId);
        if (!access.allowed()) {
            throw new ResourceNotFoundException("Project not found");
        }
        if (!access.workspaceAdmin() && access.projectRole() != ProjectRole.LEAD) {
            throw new ForbiddenException("Only project leads or workspace admins can perform this action");
        }
        return project;
    }

    public boolean isLeadOrWorkspaceAdmin(Long workspaceId, Long projectId) {
        Access access = resolveAccess(workspaceId, projectId);
        return access.workspaceAdmin() || access.projectRole() == ProjectRole.LEAD;
    }

    public boolean isWorkspaceAdmin(Long workspaceId) {
        Long userId = currentUserService.getCurrentUser().id();
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
            .map(this::isWorkspaceAdmin)
            .orElse(false);
    }

    public Project requireProjectInWorkspace(Long workspaceId, Long projectId) {
        return projectRepository.findById(projectId)
            .filter(project -> project.getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Access resolveAccess(Long workspaceId, Long projectId) {
        Long userId = currentUserService.getCurrentUser().id();
        WorkspaceMember workspaceMembership = workspaceMemberRepository
            .findByWorkspaceIdAndUserId(workspaceId, userId)
            .orElse(null);
        if (workspaceMembership == null) {
            return Access.denied();
        }
        if (isWorkspaceAdmin(workspaceMembership)) {
            return new Access(true, true, null);
        }
        ProjectRole role = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(ProjectMember::getRole)
            .orElse(null);
        return new Access(role != null, false, role);
    }

    private boolean isWorkspaceAdmin(WorkspaceMember membership) {
        return membership.getRole() == Role.OWNER || membership.getRole() == Role.ADMIN;
    }

    private record Access(boolean allowed, boolean workspaceAdmin, ProjectRole projectRole) {
        private static Access denied() {
            return new Access(false, false, null);
        }
    }
}
