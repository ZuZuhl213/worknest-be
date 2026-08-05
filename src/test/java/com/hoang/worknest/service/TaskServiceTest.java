package com.hoang.worknest.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hoang.worknest.dto.task.TaskUpdateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.mapper.TaskMapper;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.AttachmentRepository;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    private static final long WORKSPACE_ID = 11L;
    private static final long PROJECT_ID = 13L;
    private static final long TASK_ID = 17L;
    private static final long USER_ID = 7L;

    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock TaskMapper taskMapper;
    @Mock UserMapper userMapper;
    @Mock CurrentUserService currentUserService;
    @Mock NotificationService notificationService;
    @Mock ActivityLogService activityLogService;
    @Mock AttachmentRepository attachmentRepository;
    @Mock FileStorageService fileStorageService;
    @Mock ProjectRepository projectRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        ProjectAuthorizationService authorization = new ProjectAuthorizationService(
            projectRepository,
            projectMemberRepository,
            workspaceMemberRepository,
            currentUserService
        );
        taskService = new TaskService(
            taskRepository,
            userRepository,
            projectMemberRepository,
            taskMapper,
            userMapper,
            currentUserService,
            notificationService,
            activityLogService,
            attachmentRepository,
            fileStorageService,
            authorization
        );
    }

    @Test
    void unrelatedMemberCannotUpdateSomeoneElsesTask() {
        Task task = taskWithReporterAndAssignee(101L, 102L);
        stubMemberAccess(ProjectRole.MEMBER, task);

        assertThrows(ForbiddenException.class,
            () -> taskService.update(WORKSPACE_ID, PROJECT_ID, TASK_ID, updateRequest(null)));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void reporterMemberCanUpdateOwnTask() {
        Task task = taskWithReporterAndAssignee(USER_ID, null);
        stubMemberAccess(ProjectRole.MEMBER, task);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.update(WORKSPACE_ID, PROJECT_ID, TASK_ID, updateRequest(null));

        verify(taskRepository).save(task);
    }

    @Test
    void assigneeMemberCanUpdateAssignedTask() {
        Task task = taskWithReporterAndAssignee(101L, USER_ID);
        stubMemberAccess(ProjectRole.MEMBER, task);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.update(WORKSPACE_ID, PROJECT_ID, TASK_ID, updateRequest(USER_ID));

        verify(taskRepository).save(task);
    }

    private void stubMemberAccess(ProjectRole projectRole, Task task) {
        User user = User.builder().id(USER_ID).build();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(task.getProject()));
        when(currentUserService.getCurrentUser())
            .thenReturn(new AuthenticatedUser(USER_ID, "user@example.com", "User", true, true, SystemRole.USER, false, 0, null));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(com.hoang.worknest.entity.WorkspaceMember.builder()
                .workspace(task.getProject().getWorkspace())
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .build()));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
            .thenReturn(Optional.of(ProjectMember.builder().user(user).role(projectRole).build()));
    }

    private Task taskWithReporterAndAssignee(Long reporterId, Long assigneeId) {
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        Project project = Project.builder().id(PROJECT_ID).workspace(workspace).build();
        return Task.builder()
            .id(TASK_ID)
            .project(project)
            .title("Task")
            .status(TaskStatus.TODO)
            .priority(TaskPriority.MEDIUM)
            .reporter(User.builder().id(reporterId).build())
            .assignee(assigneeId == null ? null : User.builder().id(assigneeId).build())
            .build();
    }

    private TaskUpdateRequest updateRequest(Long assigneeUserId) {
        return new TaskUpdateRequest(
            "Updated",
            "Description",
            TaskStatus.IN_PROGRESS,
            TaskPriority.HIGH,
            assigneeUserId,
            null
        );
    }
}
