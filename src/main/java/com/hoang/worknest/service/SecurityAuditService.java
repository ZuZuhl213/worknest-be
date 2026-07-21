package com.hoang.worknest.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.entity.SecurityAuditLog;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.repository.SecurityAuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    private final SecurityAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(User actor, User target, String action, String outcome, Map<String, ?> metadata) {
        HttpServletRequest request = currentRequest();
        repository.save(SecurityAuditLog.builder()
            .actor(actor)
            .target(target)
            .action(action)
            .outcome(outcome)
            .ipAddress(request == null ? null : truncate(request.getRemoteAddr(), 64))
            .userAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 500))
            .metadata(toJson(metadata))
            .build());
    }

    public String currentClientAddress() {
        HttpServletRequest request = currentRequest();
        return request == null ? "unknown" : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String toJson(Map<String, ?> metadata) {
        try {
            return metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
