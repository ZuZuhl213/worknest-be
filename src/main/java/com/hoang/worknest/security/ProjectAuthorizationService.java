package com.hoang.worknest.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * Single source of truth for project-level authorization.
 *
 * <p>Workspace OWNER/ADMIN are treated as LEAD on every project in their workspace, so they do not
 * need a {@code project_members} row to manage a project.
 */
@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CurrentUserService currentUserService;

    /**
     * The effective permissions of the current user on a project, as exposed to clients so the UI
     * never has to re-derive the matrix itself.
     */
    public record ProjectPermissions(
        ProjectRole effectiveRole,
        boolean canViewProject,
        boolean canCreateTask,
        boolean canAssignTask,
        boolean canComment,
        boolean canManageProject,
        boolean canManageMembers
    ) {
        private static ProjectPermissions of(ProjectRole role, boolean archived, boolean workspaceAdmin, boolean taskManager) {
            if (role == null && !taskManager) {
                return new ProjectPermissions(null, false, false, false, false, false, false);
            }
            boolean lead = workspaceAdmin || role == ProjectRole.LEAD;
            boolean writer = lead || role == ProjectRole.MEMBER;
            boolean writable = (writer || taskManager) && !archived;
            return new ProjectPermissions(
                lead ? ProjectRole.LEAD : role,
                true,
                writable,
                (lead || taskManager) && !archived,
                writable,
                lead && !archived,
                lead && !archived
            );
        }
    }

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
        if (!access.taskManager()
            && access.projectRole() != ProjectRole.LEAD
            && access.projectRole() != ProjectRole.MEMBER) {
            throw new ForbiddenException("Project viewers have read-only access");
        }
        requireNotArchived(project);
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
        requireNotArchived(project);
        return project;
    }

    /**
     * Asserts the current user may modify this specific task.
     *
     * <p>LEAD (and workspace admins) may write any task in the project. MEMBER may only write tasks
     * they reported or are assigned to. VIEWER may not write at all.
     */
    public Task requireTaskWriter(Long workspaceId, Long projectId, Task task) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        Access access = resolveAccess(workspaceId, projectId);
        if (!access.allowed()) {
            throw new ResourceNotFoundException("Project not found");
        }
        requireNotArchived(project);

        if (access.taskManager() || access.projectRole() == ProjectRole.LEAD) {
            return task;
        }
        if (access.projectRole() != ProjectRole.MEMBER) {
            throw new ForbiddenException("Project viewers have read-only access");
        }

        Long userId = currentUserService.getCurrentUser().id();
        if (!isReporter(task, userId) && !isAssignee(task, userId)) {
            throw new ForbiddenException(
                "Project members can only modify tasks they reported or are assigned to");
        }
        return task;
    }

    public ProjectPermissions resolvePermissions(Long workspaceId, Long projectId) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        return resolvePermissions(project);
    }

    public ProjectPermissions resolvePermissions(Project project) {
        Access access = resolveAccess(project.getWorkspace().getId(), project.getId());
        return ProjectPermissions.of(
            effectiveRole(access), Boolean.TRUE.equals(project.getArchived()), access.workspaceAdmin(), access.taskManager()
        );
    }

    /**
     * Resolves permissions for many projects at once without a query per project.
     *
     * @return project id -> permissions, for every project supplied
     */
    public Map<Long, ProjectPermissions> resolvePermissions(Long workspaceId, Iterable<Project> projects) {
        Long userId = currentUserService.getCurrentUser().id();
        boolean workspaceAdmin = isWorkspaceAdmin(workspaceId);
        boolean taskManager = isWorkspaceTaskManager(workspaceId);
        Map<Long, ProjectRole> rolesByProject = taskManager
            ? Map.of()
            : projectMemberRepository.findByProjectWorkspaceIdAndUserId(workspaceId, userId).stream()
                .collect(Collectors.toMap(
                    member -> member.getProject().getId(),
                    ProjectMember::getRole,
                    (first, ignored) -> first
                ));

        Map<Long, ProjectPermissions> permissions = new LinkedHashMap<>();
        for (Project project : projects) {
            ProjectRole role = workspaceAdmin ? ProjectRole.LEAD : rolesByProject.get(project.getId());
            permissions.put(
                project.getId(),
                ProjectPermissions.of(role, Boolean.TRUE.equals(project.getArchived()), workspaceAdmin, taskManager)
            );
        }
        return permissions;
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

    public boolean isWorkspaceTaskManager(Long workspaceId) {
        Long userId = currentUserService.getCurrentUser().id();
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
            .map(this::isTaskManager)
            .orElse(false);
    }

    public Project requireTaskManager(Long workspaceId, Long projectId) {
        Project project = requireProjectInWorkspace(workspaceId, projectId);
        Access access = resolveAccess(workspaceId, projectId);
        if (!access.allowed()) {
            throw new ResourceNotFoundException("Project not found");
        }
        if (!access.taskManager() && access.projectRole() != ProjectRole.LEAD) {
            throw new ForbiddenException("Only project leads, workspace admins, or workspace managers can manage tasks");
        }
        requireNotArchived(project);
        return project;
    }

    public Project requireProjectInWorkspace(Long workspaceId, Long projectId) {
        return projectRepository.findById(projectId)
            .filter(project -> project.getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void requireNotArchived(Project project) {
        if (Boolean.TRUE.equals(project.getArchived())) {
            throw new ConflictException("Project is archived and cannot be modified");
        }
    }

    private boolean isReporter(Task task, Long userId) {
        User reporter = task.getReporter();
        return reporter != null && Objects.equals(reporter.getId(), userId);
    }

    private boolean isAssignee(Task task, Long userId) {
        User assignee = task.getAssignee();
        return assignee != null && Objects.equals(assignee.getId(), userId);
    }

    private ProjectRole effectiveRole(Access access) {
        if (access.workspaceAdmin()) {
            return ProjectRole.LEAD;
        }
        return access.projectRole();
    }

    private Access resolveAccess(Long workspaceId, Long projectId) {
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        if (currentUser.systemRole() == com.hoang.worknest.enums.SystemRole.SYSTEM_ADMIN) {
            throw new ForbiddenException("System administrators cannot access project resources");
        }
        Long userId = currentUser.id();
        WorkspaceMember workspaceMembership = workspaceMemberRepository
            .findByWorkspaceIdAndUserId(workspaceId, userId)
            .orElse(null);
        if (workspaceMembership == null) {
            return Access.denied();
        }
        if (isWorkspaceAdmin(workspaceMembership)) {
            return new Access(true, true, true, null);
        }
        if (isTaskManager(workspaceMembership)) {
            return new Access(true, false, true, null);
        }
        ProjectRole role = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(ProjectMember::getRole)
            .orElse(null);
        return new Access(role != null, false, false, role);
    }

    private boolean isWorkspaceAdmin(WorkspaceMember membership) {
        return membership.getRole() == WorkspaceRole.OWNER || membership.getRole() == WorkspaceRole.ADMIN;
    }

    private boolean isTaskManager(WorkspaceMember membership) {
        return isWorkspaceAdmin(membership) || membership.getRole() == WorkspaceRole.MANAGER;
    }

    private record Access(boolean allowed, boolean workspaceAdmin, boolean taskManager, ProjectRole projectRole) {
        private static Access denied() {
            return new Access(false, false, false, null);
        }
    }
}
