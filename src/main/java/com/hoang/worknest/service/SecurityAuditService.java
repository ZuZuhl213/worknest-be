package com.hoang.worknest.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.log.SecurityAuditLogResponse;
import com.hoang.worknest.entity.SecurityAuditLog;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.SecurityAuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    private final SecurityAuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

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

    @Transactional(readOnly = true)
    public PagedResponse<SecurityAuditLogResponse> getAuditLogs(int page, int size) {
        Page<SecurityAuditLog> logs = repository.findAllByOrderByCreatedAtDesc(
            PageRequest.of(validatePage(page), validateSize(size))
        );
        return new PagedResponse<>(
            logs.getContent().stream().map(this::toResponse).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements(),
            logs.getTotalPages(),
            logs.isFirst(),
            logs.isLast()
        );
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

    private SecurityAuditLogResponse toResponse(SecurityAuditLog log) {
        return new SecurityAuditLogResponse(
            log.getId(),
            log.getActor() == null ? null : userMapper.toResponse(log.getActor()),
            log.getTarget() == null ? null : userMapper.toResponse(log.getTarget()),
            log.getAction(),
            log.getOutcome(),
            log.getIpAddress(),
            log.getUserAgent(),
            parseJson(log.getMetadata()),
            log.getCreatedAt()
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        return size;
    }
}
