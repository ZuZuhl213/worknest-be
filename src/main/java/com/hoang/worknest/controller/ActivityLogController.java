package com.hoang.worknest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.log.ActivityLogResponse;
import com.hoang.worknest.service.ActivityLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}")
@RequiredArgsConstructor
public class ActivityLogController {
    private final ActivityLogService activityLogService;

    @GetMapping("/activity")
    public ResponseEntity<PagedResponse<ActivityLogResponse>> getProjectActivity(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(activityLogService.getProjectActivity(workspaceId, projectId, page, size));
    }

    @GetMapping("/tasks/{taskId}/activity")
    public ResponseEntity<PagedResponse<ActivityLogResponse>> getTaskActivity(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(activityLogService.getTaskActivity(workspaceId, projectId, taskId, page, size));
    }
}
