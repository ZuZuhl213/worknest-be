package com.hoang.worknest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.user.AdminOverviewResponse;
import com.hoang.worknest.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<AdminOverviewResponse> getOverview() {
        return ResponseEntity.ok(userService.getAdminOverview());
    }
}
