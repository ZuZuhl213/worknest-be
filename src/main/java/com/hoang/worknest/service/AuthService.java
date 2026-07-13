package com.hoang.worknest.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.auth.AuthResponse;
import com.hoang.worknest.dto.auth.CurrentUserResponse;
import com.hoang.worknest.dto.auth.AuthUserResponse;
import com.hoang.worknest.dto.auth.LoginRequest;
import com.hoang.worknest.dto.auth.RefreshTokenRequest;
import com.hoang.worknest.dto.auth.RegisterRequest;
import com.hoang.worknest.entity.RefreshToken;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.InvalidRefreshTokenException;
import com.hoang.worknest.repository.RefreshTokenRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.security.AuthenticatedUser;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
        }

        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName())
            .avatarUrl(request.avatarUrl())
            .isActive(Boolean.TRUE)
            .emailVerified(Boolean.FALSE)
            .build();

        User savedUser = userRepository.save(user);
        refreshTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        return issueTokens(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        revokeActiveRefreshTokens(user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired or was revoked");
        }

        refreshToken.setRevoked(Boolean.TRUE);
        refreshTokenRepository.save(refreshToken);

        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevoked(Boolean.TRUE);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        return new CurrentUserResponse(
            currentUser.id(),
            currentUser.email(),
            currentUser.fullName(),
            currentUser.emailVerified(),
            currentUser.isActive(),
            currentUser.lastLoginAt()
        );
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        OffsetDateTime accessExpiresAt = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(jwtService.getAccessTokenExpiresAt().toEpochMilli()),
            OffsetDateTime.now().getOffset()
        );

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .token(generateRefreshTokenValue())
            .expiresAt(OffsetDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000L))
            .revoked(Boolean.FALSE)
            .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
            "Bearer",
            accessToken,
            accessExpiresAt,
            refreshToken.getToken(),
            refreshToken.getExpiresAt(),
            new AuthUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getEmailVerified()
            )
        );
    }

    private void revokeActiveRefreshTokens(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        for (RefreshToken token : activeTokens) {
            token.setRevoked(Boolean.TRUE);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }

    private String generateRefreshTokenValue() {
        byte[] tokenBytes = new byte[48];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }
}
