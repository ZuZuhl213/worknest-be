package com.hoang.worknest.security;

import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final CurrentUserService currentUserService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public Workspace requireWorkspaceMember(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        rejectSystemAdmin();
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUser.id())
            .orElseThrow(() -> new ForbiddenException("You are not a member of this workspace"));

        return workspace;
    }

    public Workspace requireWorkspaceAdmin(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        rejectSystemAdmin();
        WorkspaceMember membership = requireCurrentUserMembership(workspaceId);

        if (membership.getRole() != WorkspaceRole.OWNER && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new ForbiddenException("You do not have permission to manage this workspace");
        }

        return workspace;
    }

    public Workspace requireWorkspaceOwner(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        rejectSystemAdmin();
        WorkspaceMember membership = requireCurrentUserMembership(workspaceId);
        if (membership.getRole() != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only workspace owner can perform this action");
        }

        return workspace;
    }

    public WorkspaceMember requireCurrentUserMembership(Long workspaceId) {
        rejectSystemAdmin();
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUser.id())
            .orElseThrow(() -> new ForbiddenException("You are not a member of this workspace"));
    }

    private void rejectSystemAdmin() {
        if (currentUserService.getCurrentUser().systemRole() == com.hoang.worknest.enums.SystemRole.SYSTEM_ADMIN) {
            throw new ForbiddenException("System administrators cannot access workspace resources");
        }
    }
}
