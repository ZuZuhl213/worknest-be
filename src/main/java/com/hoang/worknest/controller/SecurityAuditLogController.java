package com.hoang.worknest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.log.SecurityAuditLogResponse;
import com.hoang.worknest.service.SecurityAuditService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/security-audit-logs")
@RequiredArgsConstructor
public class SecurityAuditLogController {
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public ResponseEntity<PagedResponse<SecurityAuditLogResponse>> getSecurityAuditLogs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(securityAuditService.getAuditLogs(page, size));
    }
}
