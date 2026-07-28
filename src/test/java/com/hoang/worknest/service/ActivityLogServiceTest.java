package com.hoang.worknest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.entity.ActivityLog;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.ActivityLogRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.security.ProjectAuthorizationService;

class ActivityLogServiceTest {

    @Test
    void serializesTaskTitleWithQuotesAsParseableJsonDetails() throws Exception {
        ActivityLogRepository activityLogRepository = mock(ActivityLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ActivityLogService activityLogService = new ActivityLogService(
            activityLogRepository,
            objectMapper,
            mock(ProjectAuthorizationService.class),
            mock(TaskRepository.class),
            mock(UserMapper.class)
        );
        String title = "Fix \"quoted\" task title";

        Workspace workspace = Workspace.builder().id(1L).build();
        Project project = Project.builder().id(2L).workspace(workspace).build();
        Task task = Task.builder().id(3L).project(project).title(title).build();
        User actor = User.builder().id(4L).build();

        activityLogService.log(
            workspace,
            project,
            task,
            actor,
            "TASK_DELETED",
            "TASK",
            task.getId(),
            Map.of("title", task.getTitle())
        );

        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(logCaptor.capture());

        JsonNode details = objectMapper.readTree(logCaptor.getValue().getDetails());
        assertEquals(title, details.get("title").asText());
    }
}
