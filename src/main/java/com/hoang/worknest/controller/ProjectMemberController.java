package com.hoang.worknest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.project.ProjectAddMemberRequest;
import com.hoang.worknest.dto.project.ProjectChangeMemberRoleRequest;
import com.hoang.worknest.dto.project.ProjectMemberResponse;
import com.hoang.worknest.service.ProjectMemberService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<PagedResponse<ProjectMemberResponse>> getMembers(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(projectMemberService.getMembers(workspaceId, projectId, page, size));
    }

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> addMember(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @Valid @RequestBody ProjectAddMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(projectMemberService.addMember(workspaceId, projectId, request));
    }

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ProjectMemberResponse> changeMemberRole(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long memberId,
        @Valid @RequestBody ProjectChangeMemberRoleRequest request
    ) {
        return ResponseEntity.ok(projectMemberService.changeMemberRole(workspaceId, projectId, memberId, request));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
        @PathVariable Long workspaceId,
        @PathVariable Long projectId,
        @PathVariable Long memberId
    ) {
        projectMemberService.removeMember(workspaceId, projectId, memberId);
        return ResponseEntity.noContent().build();
    }
}
