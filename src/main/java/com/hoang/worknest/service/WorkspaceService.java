package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.task.TaskUserResponse;
import com.hoang.worknest.dto.task.WorkspaceTaskResponse;
import com.hoang.worknest.dto.workspace.WorkspaceChangeMemberRoleRequest;
import com.hoang.worknest.dto.workspace.WorkspaceCreateRequest;
import com.hoang.worknest.dto.workspace.WorkspaceInviteMemberRequest;
import com.hoang.worknest.dto.workspace.WorkspaceMemberResponse;
import com.hoang.worknest.dto.workspace.WorkspaceResponse;
import com.hoang.worknest.dto.workspace.WorkspaceUpdateRequest;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.WorkspaceMemberMapper;
import com.hoang.worknest.mapper.WorkspaceMapper;
import com.hoang.worknest.repository.AttachmentRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.repository.WorkspaceRepository;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.repository.specification.TaskSpecifications;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.WorkspaceAccessService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageCleanupService storageCleanupService;
    private final UserRepository userRepository;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final CurrentUserService currentUserService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ActivityLogService activityLogService;
    private final ProjectMemberRepository projectMemberRepository;
    private final SecurityAuditService securityAuditService;
    private final TaskRepository taskRepository;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_TASK_SORT_FIELDS = Set.of(
        "createdAt", "updatedAt", "dueDate", "priority", "status", "taskNumber", "title"
    );

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
        if (currentUser.systemRole() == SystemRole.SYSTEM_ADMIN) {
            throw new ForbiddenException("System administrators cannot create workspaces");
        }
        User owner = userRepository.findByIdForUpdate(currentUser.id())
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        if (!Boolean.TRUE.equals(owner.getCanCreateWorkspace())) {
            throw new ForbiddenException("Workspace creation has not been granted");
        }
        if (workspaceRepository.countByOwnerId(owner.getId()) >= 5) {
            throw new ConflictException("Workspace creation limit of 5 has been reached");
        }

        Workspace workspace = workspaceMapper.toEntity(request);
        workspace.setOwner(owner);

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMembership = WorkspaceMember.builder()
            .workspace(savedWorkspace)
            .user(owner)
            .role(WorkspaceRole.OWNER)
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
            Map.of("slug", savedWorkspace.getSlug())
        );

        return workspaceMapper.toResponse(savedWorkspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getAll() {
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        if (currentUser.systemRole() == SystemRole.SYSTEM_ADMIN) {
            throw new ForbiddenException("System administrators cannot access workspace resources");
        }
        Long currentUserId = currentUser.id();
        return workspaceMemberRepository.findByUserId(currentUserId).stream()
            .map(WorkspaceMember::getWorkspace)
            .map(workspaceMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
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
            Map.of("slug", savedWorkspace.getSlug())
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
        attachmentRepository.findStorageObjectsByWorkspaceId(id).forEach(storageObject ->
            storageCleanupService.enqueue(storageObject.getBucketName(), storageObject.getObjectKey())
        );
        workspaceMemberRepository.deleteAll(workspaceMemberRepository.findByWorkspaceId(id));
        workspaceRepository.delete(workspace);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WorkspaceMemberResponse> getMembers(Long workspaceId, int page, int size) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Page<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(
            workspaceId,
            PageRequest.of(validatePage(page), validateSize(size))
        );
        return new PagedResponse<>(
            members.getContent().stream().map(workspaceMemberMapper::toResponse).toList(),
            members.getNumber(),
            members.getSize(),
            members.getTotalElements(),
            members.getTotalPages(),
            members.isFirst(),
            members.isLast()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<WorkspaceTaskResponse> getTasks(
        Long workspaceId,
        Long assigneeId,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        validateTaskSort(sortBy);
        Long currentUserId = currentUserService.getCurrentUser().id();
        WorkspaceMember currentMembership = workspaceAccessService.requireCurrentUserMembership(workspaceId);
        boolean workspaceAdmin = currentMembership.getRole() == WorkspaceRole.OWNER || currentMembership.getRole() == WorkspaceRole.ADMIN;
        List<Long> accessibleProjectIds = workspaceAdmin
            ? null
            : projectMemberRepository.findByProjectWorkspaceIdAndUserId(workspaceId, currentUserId).stream()
                .map(member -> member.getProject().getId())
                .toList();
        if (accessibleProjectIds != null && accessibleProjectIds.isEmpty()) {
            return new PagedResponse<>(List.of(), validatePage(page), validateSize(size), 0, 0, true, true);
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Page<Task> tasks = taskRepository.findAll(
            org.springframework.data.jpa.domain.Specification.where(TaskSpecifications.belongsToWorkspace(workspaceId))
                .and(accessibleProjectIds == null ? null : TaskSpecifications.projectIn(accessibleProjectIds))
                .and(TaskSpecifications.hasAssignee(assigneeId)),
            PageRequest.of(validatePage(page), validateSize(size), sort)
        );
        return new PagedResponse<>(
            tasks.getContent().stream().map(this::toWorkspaceTaskResponse).toList(),
            tasks.getNumber(),
            tasks.getSize(),
            tasks.getTotalElements(),
            tasks.getTotalPages(),
            tasks.isFirst(),
            tasks.isLast()
        );
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
        User invitedUser = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new ResourceNotFoundException("User to invite not found"));

        assertRoleCanBeManaged(workspaceAccessService.requireCurrentUserMembership(workspaceId).getRole(), request.role());

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
        securityAuditService.log(inviter, invitedUser, "WORKSPACE_MEMBER_ADDED", "SUCCESS",
            Map.of("workspaceId", workspaceId, "role", savedMember.getRole().name()));
        activityLogService.log(
            workspace,
            null,
            null,
            inviter,
            "WORKSPACE_MEMBER_INVITED",
            "WORKSPACE_MEMBER",
            savedMember.getId(),
            Map.of("userId", invitedUser.getId(), "role", savedMember.getRole().name())
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
        Workspace workspace = workspaceAccessService.requireWorkspaceAdmin(workspaceId);
        User actor = getCurrentUserEntity();
        WorkspaceMember member = getWorkspaceMember(workspaceId, memberId);
        WorkspaceMember actorMembership = workspaceAccessService.requireCurrentUserMembership(workspaceId);

        assertRoleCanBeManaged(actorMembership.getRole(), member.getRole());
        assertRoleCanBeManaged(actorMembership.getRole(), request.role());

        member.setRole(request.role());
        WorkspaceMember savedMember = workspaceMemberRepository.save(member);
        securityAuditService.log(actor, savedMember.getUser(), "WORKSPACE_MEMBER_ROLE_CHANGED", "SUCCESS",
            Map.of("workspaceId", workspaceId, "role", savedMember.getRole().name()));
        activityLogService.log(
            workspace,
            null,
            null,
            actor,
            "WORKSPACE_MEMBER_ROLE_CHANGED",
            "WORKSPACE_MEMBER",
            savedMember.getId(),
            Map.of("userId", savedMember.getUser().getId(), "role", savedMember.getRole().name())
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
        WorkspaceMember actorMembership = workspaceAccessService.requireCurrentUserMembership(workspaceId);

        assertRoleCanBeManaged(actorMembership.getRole(), member.getRole());

        ensureNotLastProjectLead(workspaceId, member.getUser().getId());
        projectMemberRepository.deleteByWorkspaceAndUser(workspaceId, member.getUser().getId());
        workspaceMemberRepository.delete(member);
        securityAuditService.log(actor, member.getUser(), "WORKSPACE_MEMBER_REMOVED", "SUCCESS",
            Map.of("workspaceId", workspaceId));
        activityLogService.log(
            workspace,
            null,
            null,
            actor,
            "WORKSPACE_MEMBER_REMOVED",
            "WORKSPACE_MEMBER",
            member.getId(),
            Map.of("userId", member.getUser().getId())
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

    private void assertRoleCanBeManaged(WorkspaceRole actorRole, WorkspaceRole targetRole) {
        if (targetRole == WorkspaceRole.OWNER) {
            throw new ForbiddenException("Owner role cannot be managed through this endpoint");
        }
        if (actorRole == WorkspaceRole.OWNER) {
            return;
        }
        if (actorRole == WorkspaceRole.ADMIN && targetRole != WorkspaceRole.ADMIN) {
            return;
        }
        throw new ForbiddenException("You do not have permission to manage this member role");
    }

    private void ensureNotLastProjectLead(Long workspaceId, Long userId) {
        projectMemberRepository.findByProjectWorkspaceIdAndUserId(workspaceId, userId).stream()
            .filter(projectMember -> projectMember.getRole() == ProjectRole.LEAD)
            .forEach(projectMember -> {
                if (projectMemberRepository
                    .findByProjectIdAndRoleOrderById(projectMember.getProject().getId(), ProjectRole.LEAD)
                    .size() <= 1) {
                    throw new ConflictException("Assign another lead before removing this workspace member");
                }
            });
    }

    private User getCurrentUserEntity() {
        Long currentUserId = currentUserService.getCurrentUser().id();
        return userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private WorkspaceTaskResponse toWorkspaceTaskResponse(Task task) {
        return new WorkspaceTaskResponse(
            task.getId(),
            task.getProject().getId(),
            task.getProject().getName(),
            task.getProject().getProjectKey(),
            task.getTaskNumber(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getPriority(),
            toTaskUserResponse(task.getAssignee()),
            toTaskUserResponse(task.getReporter()),
            task.getDueDate(),
            task.getStartedAt(),
            task.getCompletedAt(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }

    private TaskUserResponse toTaskUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return new TaskUserResponse(user.getId(), user.getEmail(), user.getFullName());
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return size;
    }

    private void validateTaskSort(String sortBy) {
        if (!ALLOWED_TASK_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported task sort field: " + sortBy);
        }
    }
}
