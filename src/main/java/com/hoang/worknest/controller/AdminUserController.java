package com.hoang.worknest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> search(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) Boolean emailVerified,
        @RequestParam(required = false) SystemRole role,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        return ResponseEntity.ok(userService.searchUsers(search, active, emailVerified, role, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserForAdmin(id));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setUserActive(id, false));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<UserResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setUserActive(id, true));
    }

    @PostMapping("/{id}/workspace-creation/enable")
    public ResponseEntity<UserResponse> enableWorkspaceCreation(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setWorkspaceCreation(id, true));
    }

    @PostMapping("/{id}/workspace-creation/disable")
    public ResponseEntity<UserResponse> disableWorkspaceCreation(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setWorkspaceCreation(id, false));
    }
}
