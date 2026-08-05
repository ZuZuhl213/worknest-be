package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.project.ProjectCreateRequest;
import com.hoang.worknest.dto.project.ProjectPermissionsResponse;
import com.hoang.worknest.dto.project.ProjectResponse;
import com.hoang.worknest.dto.project.ProjectUpdateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.ProjectMapper;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;
import com.hoang.worknest.security.WorkspaceAccessService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMapper projectMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PROJECTS_BY_WORKSPACE, key = "#workspaceId")
    public ProjectResponse create(Long workspaceId, ProjectCreateRequest request) {
        Workspace workspace = workspaceAccessService.requireWorkspaceAdmin(workspaceId);
        String normalizedProjectKey = normalizeProjectKey(request.projectKey());
        validateProjectUniqueness(workspaceId, normalizedProjectKey, request.name(), null);

        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        User creator = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Project project = projectMapper.toEntity(request);
        project.setProjectKey(normalizedProjectKey);
        project.setWorkspace(workspace);
        project.setCreatedBy(creator);

        Project savedProject = projectRepository.save(project);

        // The creator becomes the project LEAD so every project always has an owner
        // and the "at least one LEAD" invariant holds from the moment it is created.
        projectMemberRepository.save(ProjectMember.builder()
            .project(savedProject)
            .user(creator)
            .role(ProjectRole.LEAD)
            .addedBy(creator)
            .joinedAt(OffsetDateTime.now())
            .build());

        activityLogService.log(
            workspace,
            savedProject,
            null,
            creator,
            "PROJECT_CREATED",
            "PROJECT",
            savedProject.getId(),
            Map.of("projectKey", savedProject.getProjectKey())
        );

        return toResponse(savedProject, projectAuthorizationService.resolvePermissions(savedProject));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getByWorkspace(Long workspaceId) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        List<Project> projects = projectAuthorizationService.isWorkspaceTaskManager(workspaceId)
            ? projectRepository.findByWorkspaceId(workspaceId)
            : projectRepository.findAccessibleByWorkspaceAndUser(workspaceId, currentUser.id());
        Map<Long, ProjectAuthorizationService.ProjectPermissions> permissionsByProject =
            projectAuthorizationService.resolvePermissions(workspaceId, projects);
        return projects.stream()
            .map(project -> toResponse(project, permissionsByProject.get(project.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long workspaceId, Long projectId) {
        Project project = projectAuthorizationService.requireAccess(workspaceId, projectId);
        return toResponse(project, projectAuthorizationService.resolvePermissions(project));
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.PROJECTS_BY_WORKSPACE, key = "#workspaceId"),
        @CacheEvict(cacheNames = CacheConfig.PROJECT_DETAIL, key = "#projectId")
    })
    public ProjectResponse update(Long workspaceId, Long projectId, ProjectUpdateRequest request) {
        Project project = projectAuthorizationService.requireLead(workspaceId, projectId);
        Workspace workspace = project.getWorkspace();
        String normalizedProjectKey = normalizeProjectKey(request.projectKey());
        validateProjectUniqueness(workspaceId, normalizedProjectKey, request.name(), projectId);

        projectMapper.updateEntity(request, project);
        project.setProjectKey(normalizedProjectKey);
        Project savedProject = projectRepository.save(project);

        User actor = userRepository.findById(currentUserService.getCurrentUser().id())
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        activityLogService.log(
            workspace,
            savedProject,
            null,
            actor,
            "PROJECT_UPDATED",
            "PROJECT",
            savedProject.getId(),
            Map.of("projectKey", savedProject.getProjectKey())
        );

        return toResponse(savedProject, projectAuthorizationService.resolvePermissions(savedProject));
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.PROJECTS_BY_WORKSPACE, key = "#workspaceId"),
        @CacheEvict(cacheNames = CacheConfig.PROJECT_DETAIL, key = "#projectId"),
        @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, key = "#projectId")
    })
    public void delete(Long workspaceId, Long projectId) {
        Project project = projectAuthorizationService.requireLead(workspaceId, projectId);
        Workspace workspace = project.getWorkspace();
        User actor = userRepository.findById(currentUserService.getCurrentUser().id())
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        activityLogService.log(
            workspace,
            project,
            null,
            actor,
            "PROJECT_DELETED",
            "PROJECT",
            project.getId(),
            Map.of("projectKey", project.getProjectKey())
        );
        projectRepository.delete(project);
    }

    private void validateProjectUniqueness(Long workspaceId, String projectKey, String name, Long projectIdToExclude) {
        projectRepository.findByWorkspaceIdAndProjectKey(workspaceId, projectKey).ifPresent(existingProject -> {
            if (projectIdToExclude == null || !existingProject.getId().equals(projectIdToExclude)) {
                throw new ConflictException("Project key already exists in this workspace");
            }
        });

        projectRepository.findByWorkspaceIdAndName(workspaceId, name).ifPresent(existingProject -> {
            if (projectIdToExclude == null || !existingProject.getId().equals(projectIdToExclude)) {
                throw new ConflictException("Project name already exists in this workspace");
            }
        });
    }

    private String normalizeProjectKey(String projectKey) {
        return projectKey == null ? null : projectKey.trim().toUpperCase();
    }

    private ProjectResponse toResponse(
        Project project,
        ProjectAuthorizationService.ProjectPermissions permissions
    ) {
        ProjectPermissionsResponse permissionsResponse = new ProjectPermissionsResponse(
            permissions.canViewProject(),
            permissions.canCreateTask(),
            permissions.canAssignTask(),
            permissions.canComment(),
            permissions.canManageProject(),
            permissions.canManageMembers()
        );
        return projectMapper.toResponse(project, permissions.effectiveRole(), permissionsResponse);
    }
}
