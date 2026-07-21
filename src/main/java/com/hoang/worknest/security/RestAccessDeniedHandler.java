package com.hoang.worknest.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String message = accessDeniedException instanceof MissingCsrfTokenException
            ? "CSRF token is missing. Refresh the page and try again."
            : accessDeniedException instanceof CsrfException
                ? "CSRF token is invalid or expired. Refresh the page and try again."
                : "You do not have permission to perform this action.";

        objectMapper.writeValue(response.getOutputStream(), Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "status", HttpStatus.FORBIDDEN.value(),
            "error", HttpStatus.FORBIDDEN.getReasonPhrase(),
            "message", message
        ));
    }
}
