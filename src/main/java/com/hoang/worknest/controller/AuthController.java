package com.hoang.worknest.controller;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoang.worknest.dto.auth.AuthResponse;
import com.hoang.worknest.dto.auth.CurrentUserResponse;
import com.hoang.worknest.dto.auth.LoginRequest;
import com.hoang.worknest.dto.auth.RegisterRequest;
import com.hoang.worknest.service.AuthService;
import com.hoang.worknest.service.AuthService.AuthSession;

import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return sessionResponse(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return sessionResponse(authService.login(request), HttpStatus.OK);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = "worknest_rt", required = false) String refreshToken
    ) {
        return sessionResponse(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = "worknest_rt", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    private ResponseEntity<AuthResponse> sessionResponse(AuthSession session, HttpStatus status) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session).toString())
            .body(session.response());
    }

    private ResponseCookie refreshCookie(AuthSession session) {
        long maxAge = Math.max(0, Duration.between(OffsetDateTime.now(), session.refreshExpiresAt()).toSeconds());
        return ResponseCookie.from("worknest_rt", session.refreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(maxAge)
            .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from("worknest_rt", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(0)
            .build();
    }
}
