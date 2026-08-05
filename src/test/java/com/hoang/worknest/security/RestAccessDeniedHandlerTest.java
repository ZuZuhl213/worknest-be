package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RestAccessDeniedHandlerTest {

    private RestAccessDeniedHandler handler;
    private HttpServletResponse response;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException {
        handler = new RestAccessDeniedHandler(new ObjectMapper());
        response = mock(HttpServletResponse.class);
        outputStream = new ByteArrayOutputStream();
        org.mockito.Mockito.when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
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
    void writesForbiddenResponseForGenericAccessDeniedException() throws Exception {
        handler.handle(mock(HttpServletRequest.class), response, new AccessDeniedException("nope"));

        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("You do not have permission to perform this action."));
    }

    @Test
    void writesSpecificMessageForMissingCsrfToken() throws Exception {
        handler.handle(mock(HttpServletRequest.class), response, new MissingCsrfTokenException("missing"));

        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("CSRF token is missing."));
    }

    @Test
    void writesSpecificMessageForInvalidCsrfToken() throws Exception {
        handler.handle(mock(HttpServletRequest.class), response, new CsrfException("invalid"));

        String body = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("CSRF token is invalid or expired."));
    }
}
