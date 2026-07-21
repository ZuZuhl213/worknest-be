package com.hoang.worknest.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.project.ProjectCreateRequest;
import com.hoang.worknest.dto.project.ProjectResponse;
import com.hoang.worknest.dto.project.ProjectUpdateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.ProjectMapper;
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
    private final ProjectMapper projectMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PROJECTS_BY_WORKSPACE, allEntries = true)
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
        activityLogService.log(
            workspace,
            savedProject,
            null,
            creator,
            "PROJECT_CREATED",
            "PROJECT",
            savedProject.getId(),
            "{\"projectKey\":\"" + savedProject.getProjectKey() + "\"}"
        );

        return projectMapper.toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getByWorkspace(Long workspaceId) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        List<Project> projects = projectAuthorizationService.isWorkspaceAdmin(workspaceId)
            ? projectRepository.findByWorkspaceId(workspaceId)
            : projectRepository.findAccessibleByWorkspaceAndUser(workspaceId, currentUser.id());
        return projects.stream()
            .map(projectMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long workspaceId, Long projectId) {
        return projectMapper.toResponse(projectAuthorizationService.requireAccess(workspaceId, projectId));
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.PROJECTS_BY_WORKSPACE,
            CacheConfig.PROJECT_DETAIL,
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
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
            "{\"projectKey\":\"" + savedProject.getProjectKey() + "\"}"
        );

        return projectMapper.toResponse(savedProject);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.PROJECTS_BY_WORKSPACE,
            CacheConfig.PROJECT_DETAIL,
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
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
            "{\"projectKey\":\"" + project.getProjectKey() + "\"}"
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
}
