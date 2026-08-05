package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationServiceTest {
    private static final long USER_ID = 7L;
    private static final long WORKSPACE_ID = 11L;
    private static final long PROJECT_ID = 13L;

    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock CurrentUserService currentUserService;

    private ProjectAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        authorization = new ProjectAuthorizationService(
            projectRepository, projectMemberRepository, workspaceMemberRepository, currentUserService
        );
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(projectRepository.findById(PROJECT_ID))
            .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).workspace(workspace).build()));
        when(currentUserService.getCurrentUser())
            .thenReturn(new AuthenticatedUser(USER_ID, "user@example.com", "User", true, true,
                com.hoang.worknest.enums.SystemRole.USER, false, 0, null));
    }

    static Stream<Arguments> roleMatrix() {
        return Stream.of(
            Arguments.of(WorkspaceRole.OWNER, null, true, true, true),
            Arguments.of(WorkspaceRole.ADMIN, null, true, true, true),
            Arguments.of(WorkspaceRole.MANAGER, null, true, true, false),
            Arguments.of(WorkspaceRole.MEMBER, ProjectRole.LEAD, true, true, true),
            Arguments.of(WorkspaceRole.MEMBER, ProjectRole.MEMBER, true, true, false),
            Arguments.of(WorkspaceRole.MEMBER, ProjectRole.VIEWER, true, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("roleMatrix")
    void enforcesRoleMatrix(WorkspaceRole workspaceRole, ProjectRole projectRole,
                            boolean canRead, boolean canWrite, boolean canLead) {
        User user = User.builder().id(USER_ID).build();
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(WorkspaceMember.builder().workspace(workspace).user(user).role(workspaceRole).build()));
        if (workspaceRole == WorkspaceRole.MEMBER) {
            when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectMember.builder().user(user).role(projectRole).build()));
        }

        assertPermission(canRead, () -> authorization.requireAccess(WORKSPACE_ID, PROJECT_ID));
        assertPermission(canWrite, () -> authorization.requireMember(WORKSPACE_ID, PROJECT_ID));
        assertPermission(canLead, () -> authorization.requireLead(WORKSPACE_ID, PROJECT_ID));
    }

    @Test
    void managerCanManageTasksButCannotManageProject() {
        User user = User.builder().id(USER_ID).build();
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(WorkspaceMember.builder()
                .workspace(workspace).user(user).role(WorkspaceRole.MANAGER).build()));

        assertDoesNotThrow(() -> authorization.requireTaskManager(WORKSPACE_ID, PROJECT_ID));
        assertThrows(ForbiddenException.class, () -> authorization.requireLead(WORKSPACE_ID, PROJECT_ID));
    }

    @Test
    void hidesPrivateProjectFromNonMembers() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> authorization.requireAccess(WORKSPACE_ID, PROJECT_ID));
        assertThrows(ResourceNotFoundException.class,
            () -> authorization.requireMember(WORKSPACE_ID, PROJECT_ID));
        assertThrows(ResourceNotFoundException.class,
            () -> authorization.requireLead(WORKSPACE_ID, PROJECT_ID));
    }

    @ParameterizedTest
    @MethodSource("taskWriterMatrix")
    void enforcesTaskWriterMatrix(ProjectRole projectRole, boolean reporter, boolean assignee, boolean allowed) {
        User user = User.builder().id(USER_ID).build();
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.MEMBER).build()));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
            .thenReturn(Optional.of(ProjectMember.builder().user(user).role(projectRole).build()));

        Task task = Task.builder()
            .reporter(User.builder().id(reporter ? USER_ID : 101L).build())
            .assignee(assignee ? User.builder().id(USER_ID).build() : User.builder().id(102L).build())
            .build();

        assertPermission(allowed, () -> authorization.requireTaskWriter(WORKSPACE_ID, PROJECT_ID, task));
    }

    @Test
    void blocksWritesButAllowsReadsForArchivedProjects() {
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        Project archivedProject = Project.builder()
            .id(PROJECT_ID)
            .workspace(workspace)
            .archived(true)
            .build();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(archivedProject));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(WorkspaceMember.builder()
                .workspace(workspace)
                .user(User.builder().id(USER_ID).build())
                .role(WorkspaceRole.MEMBER)
                .build()));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
            .thenReturn(Optional.of(ProjectMember.builder().role(ProjectRole.LEAD).build()));

        assertDoesNotThrow(() -> authorization.requireAccess(WORKSPACE_ID, PROJECT_ID));
        assertThrows(ConflictException.class, () -> authorization.requireMember(WORKSPACE_ID, PROJECT_ID));
        assertThrows(ConflictException.class, () -> authorization.requireLead(WORKSPACE_ID, PROJECT_ID));
    }

    static Stream<Arguments> taskWriterMatrix() {
        return Stream.of(
            Arguments.of(ProjectRole.MEMBER, true, false, true),
            Arguments.of(ProjectRole.MEMBER, false, true, true),
            Arguments.of(ProjectRole.MEMBER, false, false, false),
            Arguments.of(ProjectRole.VIEWER, true, true, false),
            Arguments.of(ProjectRole.LEAD, false, false, true)
        );
    }

    private void assertPermission(boolean allowed, Runnable action) {
        if (allowed) assertDoesNotThrow(action::run);
        else assertThrows(ForbiddenException.class, action::run);
    }
}
