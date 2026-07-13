package com.hoang.worknest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.project.ProjectCreateRequest;
import com.hoang.worknest.dto.project.ProjectResponse;
import com.hoang.worknest.dto.project.ProjectUpdateRequest;
import com.hoang.worknest.service.ProjectService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
        @PathVariable Long workspaceId,
        @Valid @RequestBody ProjectCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(workspaceId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getByWorkspace(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(projectService.getByWorkspace(workspaceId));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getById(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectService.getById(workspaceId, projectId));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @Valid @RequestBody ProjectUpdateRequest request
    ) {
        return ResponseEntity.ok(projectService.update(workspaceId, projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId
    ) {
        projectService.delete(workspaceId, projectId);
        return ResponseEntity.noContent().build();
    }
}
