package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.project.ProjectAddMemberRequest;
import com.hoang.worknest.dto.project.ProjectChangeMemberRoleRequest;
import com.hoang.worknest.dto.project.ProjectMemberResponse;
import com.hoang.worknest.dto.project.ProjectMemberUserResponse;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final CurrentUserService currentUserService;
    private final SecurityAuditService securityAuditService;

    public void requireProjectAccess(Long workspaceId, Long projectId) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
    }

    /**
     * Asserts that the current user can CREATE/EDIT tasks (MEMBER or LEAD — or workspace ADMIN/OWNER)
     */
    public void requireProjectMember(Long workspaceId, Long projectId) {
        projectAuthorizationService.requireMember(workspaceId, projectId);
    }

    /**
     * Asserts that the current user can MANAGE project tasks or members (LEAD — or workspace ADMIN/OWNER)
     */
    public void requireProjectLead(Long workspaceId, Long projectId) {
        projectAuthorizationService.requireLead(workspaceId, projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long workspaceId, Long projectId) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        return projectMemberRepository.findByProjectId(projectId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long workspaceId, Long projectId, ProjectAddMemberRequest request) {
        Project project = projectAuthorizationService.requireLead(workspaceId, projectId);
        User actor = requireCurrentUserEntity();

        User targetUser = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.email()));

        // Target must be a member of the workspace first
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUser.getId())
            .orElseThrow(() -> new ForbiddenException("User must be a workspace member before being added to a project"));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, targetUser.getId())) {
            throw new ConflictException("User is already a member of this project");
        }

        ProjectMember member = ProjectMember.builder()
            .project(project)
            .user(targetUser)
            .role(request.role())
            .addedBy(actor)
            .joinedAt(OffsetDateTime.now())
            .build();

        ProjectMember saved = projectMemberRepository.save(member);
        securityAuditService.log(actor, targetUser, "PROJECT_MEMBER_ADDED", "SUCCESS",
            Map.of("workspaceId", workspaceId, "projectId", projectId, "role", request.role().name()));
        return toResponse(saved);
    }

    @Transactional
    public ProjectMemberResponse changeMemberRole(Long workspaceId, Long projectId, Long memberId, ProjectChangeMemberRoleRequest request) {
        projectAuthorizationService.requireLead(workspaceId, projectId);

        ProjectMember member = projectMemberRepository.findById(memberId)
            .filter(m -> m.getProject().getId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Project member not found"));

        ProjectRole oldRole = member.getRole();
        preventRemovingLastLead(projectId, member, request.role());
        member.setRole(request.role());
        ProjectMember saved = projectMemberRepository.save(member);
        securityAuditService.log(requireCurrentUserEntity(), member.getUser(), "PROJECT_MEMBER_ROLE_CHANGED", "SUCCESS",
            Map.of("workspaceId", workspaceId, "projectId", projectId,
                "oldRole", oldRole.name(), "newRole", request.role().name()));
        return toResponse(saved);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long projectId, Long memberId) {
        projectAuthorizationService.requireLead(workspaceId, projectId);

        ProjectMember member = projectMemberRepository.findById(memberId)
            .filter(m -> m.getProject().getId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Project member not found"));

        preventRemovingLastLead(projectId, member, null);
        User actor = requireCurrentUserEntity();
        projectMemberRepository.delete(member);
        securityAuditService.log(actor, member.getUser(), "PROJECT_MEMBER_REMOVED", "SUCCESS",
            Map.of("workspaceId", workspaceId, "projectId", projectId, "role", member.getRole().name()));
    }

    private void preventRemovingLastLead(Long projectId, ProjectMember member, ProjectRole replacementRole) {
        boolean demotesLead = member.getRole() == ProjectRole.LEAD && replacementRole != ProjectRole.LEAD;
        if (demotesLead
            && projectMemberRepository.findByProjectIdAndRoleOrderById(projectId, ProjectRole.LEAD).size() <= 1) {
            throw new ConflictException("A project must retain at least one project lead");
        }
    }

    private User requireCurrentUserEntity() {
        Long userId = currentUserService.getCurrentUser().id();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private ProjectMemberUserResponse toUserResponse(User user) {
        if (user == null) return null;
        return new ProjectMemberUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl());
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        return new ProjectMemberResponse(
            member.getId(),
            member.getProject().getId(),
            toUserResponse(member.getUser()),
            member.getRole(),
            toUserResponse(member.getAddedBy()),
            member.getJoinedAt(),
            member.getCreatedAt()
        );
    }
}
