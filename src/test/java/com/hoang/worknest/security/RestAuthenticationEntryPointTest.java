package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RestAuthenticationEntryPointTest {

    private RestAuthenticationEntryPoint entryPoint;
    private HttpServletResponse response;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException {
        entryPoint = new RestAuthenticationEntryPoint(new ObjectMapper());
        response = mock(HttpServletResponse.class);
        outputStream = new ByteArrayOutputStream();
        org.mockito.Mockito.when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                outputStream.write(b, off, len);
            }

            @Override
            public void write(byte[] b) throws IOException {
                outputStream.write(b);
            }
        });
    }

    @Test
    void writesUnauthorizedResponseForMissingAuthentication() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn(null);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/tasks");

        entryPoint.commence(request, response, mock(AuthenticationException.class));

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("Authentication is required."));
    }

    @Test
    void writesTokenExpiredMessageWhenBearerTokenIsPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/tasks");

        entryPoint.commence(request, response, mock(AuthenticationException.class));

        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("Access token is invalid or expired."));
    }

    @Test
    void writesRefreshMessageForRefreshEndpoint() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn(null);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/auth/refresh");

        entryPoint.commence(request, response, mock(AuthenticationException.class));

        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("Refresh session is missing or expired."));
    }
}
