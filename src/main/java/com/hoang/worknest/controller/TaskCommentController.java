package com.hoang.worknest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.notification.comment.TaskCommentCreateRequest;
import com.hoang.worknest.dto.notification.comment.TaskCommentResponse;
import com.hoang.worknest.dto.notification.comment.TaskCommentUpdateRequest;
import com.hoang.worknest.service.TaskCommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService taskCommentService;

    @PostMapping
    public ResponseEntity<TaskCommentResponse> create(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @Valid @RequestBody TaskCommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(taskCommentService.create(workspaceId, projectId, taskId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TaskCommentResponse>> getByTask(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(taskCommentService.getByTask(workspaceId, projectId, taskId, page, size));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<TaskCommentResponse> update(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @PathVariable Long commentId,
        @Valid @RequestBody TaskCommentUpdateRequest request
    ) {
        return ResponseEntity.ok(taskCommentService.update(workspaceId, projectId, taskId, commentId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @PathVariable Long commentId
    ) {
        taskCommentService.delete(workspaceId, projectId, taskId, commentId);
        return ResponseEntity.noContent().build();
    }
}
