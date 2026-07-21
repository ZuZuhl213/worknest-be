package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.Role;
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
                com.hoang.worknest.enums.SystemRole.USER, 0, null));
    }

    static Stream<Arguments> roleMatrix() {
        return Stream.of(
            Arguments.of(Role.OWNER, null, true, true, true),
            Arguments.of(Role.ADMIN, null, true, true, true),
            Arguments.of(Role.MEMBER, ProjectRole.LEAD, true, true, true),
            Arguments.of(Role.MEMBER, ProjectRole.MEMBER, true, true, false),
            Arguments.of(Role.MEMBER, ProjectRole.VIEWER, true, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("roleMatrix")
    void enforcesRoleMatrix(Role workspaceRole, ProjectRole projectRole,
                            boolean canRead, boolean canWrite, boolean canLead) {
        User user = User.builder().id(USER_ID).build();
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
            .thenReturn(Optional.of(WorkspaceMember.builder().workspace(workspace).user(user).role(workspaceRole).build()));
        if (workspaceRole == Role.MEMBER) {
            when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectMember.builder().user(user).role(projectRole).build()));
        }

        assertPermission(canRead, () -> authorization.requireAccess(WORKSPACE_ID, PROJECT_ID));
        assertPermission(canWrite, () -> authorization.requireMember(WORKSPACE_ID, PROJECT_ID));
        assertPermission(canLead, () -> authorization.requireLead(WORKSPACE_ID, PROJECT_ID));
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

    private void assertPermission(boolean allowed, Runnable action) {
        if (allowed) assertDoesNotThrow(action::run);
        else assertThrows(ForbiddenException.class, action::run);
    }
}
