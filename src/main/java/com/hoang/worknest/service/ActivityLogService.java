package com.hoang.worknest.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.log.ActivityLogResponse;
import com.hoang.worknest.entity.ActivityLog;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.ActivityLogRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.security.ProjectAuthorizationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final TaskRepository taskRepository;
    private final UserMapper userMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(
        Workspace workspace,
        Project project,
        Task task,
        User actor,
        String action,
        String entityType,
        Long entityId,
        Map<String, Object> details
    ) {
        ActivityLog activityLog = ActivityLog.builder()
            .workspace(workspace)
            .project(project)
            .task(task)
            .actor(actor)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .details(serialize(details))
            .build();
        activityLogRepository.save(activityLog);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ActivityLogResponse> getProjectActivity(
        Long workspaceId,
        Long projectId,
        int page,
        int size
    ) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        Page<ActivityLog> logs = activityLogRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId,
            PageRequest.of(validatePage(page), validateSize(size))
        );
        return toPagedResponse(logs);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ActivityLogResponse> getTaskActivity(
        Long workspaceId,
        Long projectId,
        Long taskId,
        int page,
        int size
    ) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        taskRepository.findById(taskId)
            .filter(task -> task.getProject().getId().equals(projectId))
            .filter(task -> task.getProject().getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Page<ActivityLog> logs = activityLogRepository.findByTaskIdOrderByCreatedAtDesc(
            taskId,
            PageRequest.of(validatePage(page), validateSize(size))
        );
        return toPagedResponse(logs);
    }

    private String serialize(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize activity log details", ex);
        }
    }

    private PagedResponse<ActivityLogResponse> toPagedResponse(Page<ActivityLog> logs) {
        return new PagedResponse<>(
            logs.getContent().stream().map(this::toResponse).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements(),
            logs.getTotalPages(),
            logs.isFirst(),
            logs.isLast()
        );
    }

    private ActivityLogResponse toResponse(ActivityLog log) {
        return new ActivityLogResponse(
            log.getId(),
            log.getWorkspace().getId(),
            log.getProject() == null ? null : log.getProject().getId(),
            log.getTask() == null ? null : log.getTask().getId(),
            log.getActor() == null ? null : userMapper.toResponse(log.getActor()),
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            parseJson(log.getDetails()),
            log.getCreatedAt()
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        return size;
    }
}
