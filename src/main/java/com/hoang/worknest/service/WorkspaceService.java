package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.workspace.WorkspaceChangeMemberRoleRequest;
import com.hoang.worknest.dto.workspace.WorkspaceCreateRequest;
import com.hoang.worknest.dto.workspace.WorkspaceInviteMemberRequest;
import com.hoang.worknest.dto.workspace.WorkspaceMemberResponse;
import com.hoang.worknest.dto.workspace.WorkspaceResponse;
import com.hoang.worknest.dto.workspace.WorkspaceUpdateRequest;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.Role;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.WorkspaceMemberMapper;
import com.hoang.worknest.mapper.WorkspaceMapper;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.repository.WorkspaceRepository;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.WorkspaceAccessService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final CurrentUserService currentUserService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ActivityLogService activityLogService;

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.CURRENT_USER_WORKSPACES,
            CacheConfig.WORKSPACE_DETAIL,
            CacheConfig.WORKSPACE_MEMBERS
        },
        allEntries = true
    )
    public WorkspaceResponse create(WorkspaceCreateRequest request) {
        validateSlugUniqueness(request.slug(), null);

        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        User owner = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Workspace workspace = workspaceMapper.toEntity(request);
        workspace.setOwner(owner);

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMembership = WorkspaceMember.builder()
            .workspace(savedWorkspace)
            .user(owner)
            .role(Role.OWNER)
            .joinedAt(OffsetDateTime.now())
            .invitedBy(owner)
            .build();
        workspaceMemberRepository.save(ownerMembership);
        activityLogService.log(
            savedWorkspace,
            null,
            null,
            owner,
            "WORKSPACE_CREATED",
            "WORKSPACE",
            savedWorkspace.getId(),
            "{\"slug\":\"" + savedWorkspace.getSlug() + "\"}"
        );

        return workspaceMapper.toResponse(savedWorkspace);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CURRENT_USER_WORKSPACES, key = "@currentUserService.getCurrentUser().id()")
    public List<WorkspaceResponse> getAll() {
        Long currentUserId = currentUserService.getCurrentUser().id();
        return workspaceMemberRepository.findByUserId(currentUserId).stream()
            .map(WorkspaceMember::getWorkspace)
            .map(workspaceMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.WORKSPACE_DETAIL, key = "#id + ':' + @currentUserService.getCurrentUser().id()")
    public WorkspaceResponse getById(Long id) {
        Workspace workspace = workspaceAccessService.requireWorkspaceMember(id);
        return workspaceMapper.toResponse(workspace);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.CURRENT_USER_WORKSPACES,
            CacheConfig.WORKSPACE_DETAIL,
            CacheConfig.WORKSPACE_MEMBERS,
            CacheConfig.PROJECTS_BY_WORKSPACE,
            CacheConfig.PROJECT_DETAIL,
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
    public WorkspaceResponse update(Long id, WorkspaceUpdateRequest request) {
        Workspace workspace = workspaceAccessService.requireWorkspaceAdmin(id);
        User actor = getCurrentUserEntity();

        validateSlugUniqueness(request.slug(), id);
        workspaceMapper.updateEntity(request, workspace);
        Workspace savedWorkspace = workspaceRepository.save(workspace);

        activityLogService.log(
            savedWorkspace,
            null,
            null,
            actor,
            "WORKSPACE_UPDATED",
            "WORKSPACE",
            savedWorkspace.getId(),
            "{\"slug\":\"" + savedWorkspace.getSlug() + "\"}"
        );
        return workspaceMapper.toResponse(savedWorkspace);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.CURRENT_USER_WORKSPACES,
            CacheConfig.WORKSPACE_DETAIL,
            CacheConfig.WORKSPACE_MEMBERS,
            CacheConfig.PROJECTS_BY_WORKSPACE,
            CacheConfig.PROJECT_DETAIL,
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
    public void delete(Long id) {
        Workspace workspace = workspaceAccessService.requireWorkspaceOwner(id);
        workspaceRepository.delete(workspace);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.WORKSPACE_MEMBERS, key = "#workspaceId + ':' + @currentUserService.getCurrentUser().id()")
    public List<WorkspaceMemberResponse> getMembers(Long workspaceId) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
            .map(workspaceMemberMapper::toResponse)
            .toList();
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.CURRENT_USER_WORKSPACES,
            CacheConfig.WORKSPACE_MEMBERS
        },
        allEntries = true
    )
    public WorkspaceMemberResponse inviteMember(Long workspaceId, WorkspaceInviteMemberRequest request) {
        Workspace workspace = workspaceAccessService.requireWorkspaceAdmin(workspaceId);
        User inviter = getCurrentUserEntity();
        User invitedUser = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResourceNotFoundException("User to invite not found"));

        if (request.role() == Role.OWNER) {
            throw new ForbiddenException("Owner role cannot be assigned through invite");
        }

        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, invitedUser.getId()).ifPresent(member -> {
            throw new ConflictException("User is already a member of this workspace");
        });

        WorkspaceMember member = WorkspaceMember.builder()
            .workspace(workspace)
            .user(invitedUser)
            .role(request.role())
            .joinedAt(OffsetDateTime.now())
            .invitedBy(inviter)
            .build();

        WorkspaceMember savedMember = workspaceMemberRepository.save(member);
        activityLogService.log(
            workspace,
            null,
            null,
            inviter,
            "WORKSPACE_MEMBER_INVITED",
            "WORKSPACE_MEMBER",
            savedMember.getId(),
            "{\"userId\":" + invitedUser.getId() + ",\"role\":\"" + savedMember.getRole() + "\"}"
        );

        return workspaceMemberMapper.toResponse(savedMember);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.WORKSPACE_MEMBERS, allEntries = true)
    public WorkspaceMemberResponse changeMemberRole(
        Long workspaceId,
        Long memberId,
        WorkspaceChangeMemberRoleRequest request
    ) {
        Workspace workspace = workspaceAccessService.requireWorkspaceOwner(workspaceId);
        User actor = getCurrentUserEntity();
        WorkspaceMember member = getWorkspaceMember(workspaceId, memberId);

        if (member.getRole() == Role.OWNER) {
            throw new ForbiddenException("Owner role cannot be changed");
        }
        if (request.role() == Role.OWNER) {
            throw new ForbiddenException("Owner transfer is not supported by this endpoint");
        }

        member.setRole(request.role());
        WorkspaceMember savedMember = workspaceMemberRepository.save(member);
        activityLogService.log(
            workspace,
            null,
            null,
            actor,
            "WORKSPACE_MEMBER_ROLE_CHANGED",
            "WORKSPACE_MEMBER",
            savedMember.getId(),
            "{\"userId\":" + savedMember.getUser().getId() + ",\"role\":\"" + savedMember.getRole() + "\"}"
        );

        return workspaceMemberMapper.toResponse(savedMember);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.CURRENT_USER_WORKSPACES,
            CacheConfig.WORKSPACE_MEMBERS
        },
        allEntries = true
    )
    public void removeMember(Long workspaceId, Long memberId) {
        Workspace workspace = workspaceAccessService.requireWorkspaceAdmin(workspaceId);
        User actor = getCurrentUserEntity();
        WorkspaceMember member = getWorkspaceMember(workspaceId, memberId);

        if (member.getRole() == Role.OWNER) {
            throw new ForbiddenException("Workspace owner cannot be removed");
        }

        workspaceMemberRepository.delete(member);
        activityLogService.log(
            workspace,
            null,
            null,
            actor,
            "WORKSPACE_MEMBER_REMOVED",
            "WORKSPACE_MEMBER",
            member.getId(),
            "{\"userId\":" + member.getUser().getId() + "}"
        );
    }

    private void validateSlugUniqueness(String slug, Long workspaceIdToExclude) {
        workspaceRepository.findBySlug(slug).ifPresent(existingWorkspace -> {
            if (workspaceIdToExclude == null || !existingWorkspace.getId().equals(workspaceIdToExclude)) {
                throw new ConflictException("Workspace slug already exists");
            }
        });
    }

    private WorkspaceMember getWorkspaceMember(Long workspaceId, Long memberId) {
        return workspaceMemberRepository.findById(memberId)
            .filter(member -> member.getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Workspace member not found"));
    }

    private User getCurrentUserEntity() {
        Long currentUserId = currentUserService.getCurrentUser().id();
        return userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
