package com.hoang.worknest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hoang.worknest.dto.project.ProjectCreateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.mapper.ProjectMapper;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;
import com.hoang.worknest.security.WorkspaceAccessService;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    private static final long WORKSPACE_ID = 11L;
    private static final long PROJECT_ID = 13L;
    private static final long USER_ID = 7L;

    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectMapper projectMapper;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock CurrentUserService currentUserService;
    @Mock UserRepository userRepository;
    @Mock ActivityLogService activityLogService;
    @Mock ProjectAuthorizationService projectAuthorizationService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
            projectRepository,
            projectMemberRepository,
            projectMapper,
            workspaceAccessService,
            currentUserService,
            userRepository,
            activityLogService,
            projectAuthorizationService
        );
    }

    @Test
    void createPersistsCreatorAsProjectLead() {
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        User creator = User.builder().id(USER_ID).email("user@example.com").build();
        Project project = Project.builder().name("Project").projectKey("APP").build();
        Project savedProject = Project.builder()
            .id(PROJECT_ID)
            .workspace(workspace)
            .name("Project")
            .projectKey("APP")
            .createdBy(creator)
            .build();

        when(workspaceAccessService.requireWorkspaceAdmin(WORKSPACE_ID)).thenReturn(workspace);
        when(projectRepository.findByWorkspaceIdAndProjectKey(WORKSPACE_ID, "APP")).thenReturn(Optional.empty());
        when(projectRepository.findByWorkspaceIdAndName(WORKSPACE_ID, "Project")).thenReturn(Optional.empty());
        when(currentUserService.getCurrentUser())
            .thenReturn(new AuthenticatedUser(USER_ID, "user@example.com", "User", true, true, SystemRole.USER, 0, null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(creator));
        when(projectMapper.toEntity(new ProjectCreateRequest("Project", "app", "Desc"))).thenReturn(project);
        when(projectRepository.save(project)).thenReturn(savedProject);
        when(projectAuthorizationService.resolvePermissions(savedProject))
            .thenReturn(new ProjectAuthorizationService.ProjectPermissions(
                ProjectRole.LEAD, true, true, true, true, true, true));

        projectService.create(WORKSPACE_ID, new ProjectCreateRequest("Project", "app", "Desc"));

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        ProjectMember member = memberCaptor.getValue();
        assertSame(savedProject, member.getProject());
        assertSame(creator, member.getUser());
        assertSame(creator, member.getAddedBy());
        assertEquals(ProjectRole.LEAD, member.getRole());
    }
}
