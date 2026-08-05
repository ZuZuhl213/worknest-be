package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.entity.User;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new ObjectMapper(), SECRET, 900_000, "worknest", "worknest-web");
        user = User.builder().id(42L).email("user@example.com").isActive(true).emailVerified(true).tokenVersion(3).build();
    }

    @Test
    void acceptsValidToken() {
        assertTrue(jwtService.isAccessTokenValid(jwtService.generateAccessToken(user), user));
    }

    @Test
    void rejectsInactiveUserAndChangedTokenVersion() {
        String token = jwtService.generateAccessToken(user);
        user.setIsActive(false);
        assertFalse(jwtService.isAccessTokenValid(token, user));
        user.setIsActive(true);
        user.setTokenVersion(4);
        assertFalse(jwtService.isAccessTokenValid(token, user));
    }

    @Test
    void rejectsUnverifiedUser() {
        String token = jwtService.generateAccessToken(user);
        user.setEmailVerified(false);
        assertFalse(jwtService.isAccessTokenValid(token, user));
    }

    @Test
    void rejectsWrongIssuerOrAudience() {
        String token = jwtService.generateAccessToken(user);
        assertFalse(new JwtService(new ObjectMapper(), SECRET, 900_000, "other", "worknest-web")
            .isAccessTokenValid(token, user));
        assertFalse(new JwtService(new ObjectMapper(), SECRET, 900_000, "worknest", "other")
            .isAccessTokenValid(token, user));
    }

    @Test
    void rejectsWrongSignatureAndShortStartupSecret() {
        String token = jwtService.generateAccessToken(user);
        JwtService otherKey = new JwtService(new ObjectMapper(), "abcdef0123456789abcdef0123456789", 900_000,
            "worknest", "worknest-web");
        assertThrows(IllegalArgumentException.class, () -> otherKey.isAccessTokenValid(token, user));
        assertThrows(IllegalStateException.class,
            () -> new JwtService(new ObjectMapper(), "too-short", 900_000, "worknest", "worknest-web"));
    }
}
