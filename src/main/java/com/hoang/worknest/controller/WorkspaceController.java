package com.hoang.worknest.controller;

import java.util.List;

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
import com.hoang.worknest.dto.task.WorkspaceTaskResponse;
import com.hoang.worknest.dto.workspace.WorkspaceChangeMemberRoleRequest;
import com.hoang.worknest.dto.workspace.WorkspaceCreateRequest;
import com.hoang.worknest.dto.workspace.WorkspaceInviteMemberRequest;
import com.hoang.worknest.dto.workspace.WorkspaceMemberResponse;
import com.hoang.worknest.dto.workspace.WorkspaceResponse;
import com.hoang.worknest.dto.workspace.WorkspaceUpdateRequest;
import com.hoang.worknest.service.WorkspaceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getAll() {
        return ResponseEntity.ok(workspaceService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody WorkspaceUpdateRequest request
    ) {
        return ResponseEntity.ok(workspaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<PagedResponse<WorkspaceMemberResponse>> getMembers(
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(workspaceService.getMembers(id, page, size));
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<PagedResponse<WorkspaceTaskResponse>> getTasks(
        @PathVariable Long id,
        @RequestParam(required = false) Long assigneeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ResponseEntity.ok(workspaceService.getTasks(id, assigneeId, page, size, sortBy, sortDirection));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<WorkspaceMemberResponse> inviteMember(
        @PathVariable Long id,
        @Valid @RequestBody WorkspaceInviteMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.inviteMember(id, request));
    }

    @PatchMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<WorkspaceMemberResponse> changeMemberRole(
        @PathVariable Long id,
        @PathVariable Long memberId,
        @Valid @RequestBody WorkspaceChangeMemberRoleRequest request
    ) {
        return ResponseEntity.ok(workspaceService.changeMemberRole(id, memberId, request));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
        @PathVariable Long id,
        @PathVariable Long memberId
    ) {
        workspaceService.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
