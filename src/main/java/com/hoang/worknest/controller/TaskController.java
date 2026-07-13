package com.hoang.worknest.controller;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.task.TaskAssignRequest;
import com.hoang.worknest.dto.task.TaskCreateRequest;
import com.hoang.worknest.dto.task.TaskResponse;
import com.hoang.worknest.dto.task.TaskUpdateRequest;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.service.TaskService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> create(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @Valid @RequestBody TaskCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(workspaceId, projectId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TaskResponse>> getByProject(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long assigneeId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) OffsetDateTime dueFrom,
        @RequestParam(required = false) OffsetDateTime dueTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ResponseEntity.ok(taskService.getByProject(
            workspaceId, projectId, status, priority, assigneeId, search, dueFrom, dueTo, page, size, sortBy, sortDirection
        ));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getById(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(taskService.getById(workspaceId, projectId, taskId));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> update(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @Valid @RequestBody TaskUpdateRequest request
    ) {
        return ResponseEntity.ok(taskService.update(workspaceId, projectId, taskId, request));
    }

    @PatchMapping("/{taskId}/assignee")
    public ResponseEntity<TaskResponse> assign(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @Valid @RequestBody TaskAssignRequest request
    ) {
        return ResponseEntity.ok(taskService.assign(workspaceId, projectId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId
    ) {
        taskService.delete(workspaceId, projectId, taskId);
        return ResponseEntity.noContent().build();
    }
}
