package com.hoang.worknest.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String authorization = request.getHeader("Authorization");
        String message = authorization != null && authorization.startsWith("Bearer ")
            ? "Access token is invalid or expired. Please sign in again."
            : request.getRequestURI().endsWith("/refresh")
                ? "Refresh session is missing or expired. Please sign in again."
                : "Authentication is required. Please sign in.";

        objectMapper.writeValue(response.getOutputStream(), Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "status", HttpStatus.UNAUTHORIZED.value(),
            "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "message", message
        ));
    }
}
