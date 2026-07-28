package com.hoang.worknest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.auth.AuthResponse;
import com.hoang.worknest.dto.auth.AuthUserResponse;
import com.hoang.worknest.dto.auth.CurrentUserResponse;
import com.hoang.worknest.dto.auth.ForgotPasswordRequest;
import com.hoang.worknest.dto.auth.LoginRequest;
import com.hoang.worknest.dto.auth.RegisterRequest;
import com.hoang.worknest.dto.auth.ResetPasswordRequest;
import com.hoang.worknest.dto.auth.VerifyEmailRequest;
import com.hoang.worknest.entity.AccountToken;
import com.hoang.worknest.entity.RefreshToken;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.AccountTokenType;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.InvalidRefreshTokenException;
import com.hoang.worknest.exception.TooManyRequestsException;
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
    private final RateLimitService rateLimitService;
    private final SecurityAuditService securityAuditService;
    private final AccountTokenService accountTokenService;
    private final AccountEmailSender accountEmailSender;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Value("${app.email.password-reset-token-expiration-ms}")
    private long passwordResetTokenExpirationMs;

    @Value("${app.email.verification-token-expiration-ms}")
    private long emailVerificationTokenExpirationMs;

    public record AuthSession(AuthResponse response, String refreshToken, OffsetDateTime refreshExpiresAt) {
    }

    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public AuthSession register(RegisterRequest request) {
        rateLimitService.check("register-ip", securityAuditService.currentClientAddress(), 5, Duration.ofHours(1));
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email address is already registered");
        }

        User savedUser = userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName().trim())
            .avatarUrl(null)
            .isActive(Boolean.TRUE)
            .emailVerified(Boolean.FALSE)
            .build());
        securityAuditService.log(savedUser, savedUser, "ACCOUNT_REGISTERED", "SUCCESS", Map.of());
        sendVerificationEmail(savedUser);
        return issueNewFamily(savedUser);
    }

    @Transactional(noRollbackFor = {AuthenticationException.class, TooManyRequestsException.class})
    public AuthSession login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        rateLimitService.check("login-ip", securityAuditService.currentClientAddress(), 30, Duration.ofMinutes(15));
        rateLimitService.checkCurrent("login-account", email, 5, Duration.ofMinutes(15));
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            rateLimitService.increment("login-account", email, Duration.ofMinutes(15));
            securityAuditService.log(null, null, "LOGIN", "FAILED", Map.of("emailHash", hash(email)));
            throw ex;
        }

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        rateLimitService.reset("login-account", email);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        revokeAllForUser(user.getId());
        securityAuditService.log(user, user, "LOGIN", "SUCCESS", Map.of());
        return issueNewFamily(user);
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, TooManyRequestsException.class})
    public AuthSession refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        String tokenHash = hash(rawToken);
        rateLimitService.check("refresh-ip", securityAuditService.currentClientAddress(), 60, Duration.ofMinutes(15));
        rateLimitService.check("refresh-session", tokenHash, 60, Duration.ofMinutes(15));

        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        OffsetDateTime now = OffsetDateTime.now();
        if (current.getUsedAt() != null || current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId(), now);
            current.getUser().setTokenVersion(current.getUser().getTokenVersion() + 1);
            userRepository.save(current.getUser());
            securityAuditService.log(current.getUser(), current.getUser(), "REFRESH_TOKEN_REUSE", "BLOCKED", Map.of());
            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.setRevokedAt(now);
            refreshTokenRepository.save(current);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        if (!Boolean.TRUE.equals(current.getUser().getIsActive())) {
            revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Account is inactive");
        }

        String nextRawToken = generateRefreshTokenValue();
        RefreshToken next = refreshTokenRepository.save(RefreshToken.builder()
            .user(current.getUser())
            .tokenHash(hash(nextRawToken))
            .familyId(current.getFamilyId())
            .expiresAt(now.plusNanos(refreshTokenExpirationMs * 1_000_000L))
            .build());
        current.setUsedAt(now);
        current.setRevokedAt(now);
        current.setReplacedBy(next);
        refreshTokenRepository.save(current);

        return new AuthSession(buildAuthResponse(current.getUser()), nextRawToken, next.getExpiresAt());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            revokeFamily(token.getFamilyId(), OffsetDateTime.now());
            securityAuditService.log(token.getUser(), token.getUser(), "LOGOUT", "SUCCESS", Map.of());
        });
    }

    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        rateLimitService.check("forgot-password-ip", securityAuditService.currentClientAddress(), 10, Duration.ofHours(1));
        rateLimitService.check("forgot-password-account", email, 3, Duration.ofHours(1));
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            String rawToken = accountTokenService.issue(
                user,
                AccountTokenType.PASSWORD_RESET,
                Duration.ofMillis(passwordResetTokenExpirationMs)
            );
            accountEmailSender.sendPasswordReset(user, rawToken);
            securityAuditService.log(user, user, "PASSWORD_RESET_REQUESTED", "SUCCESS", Map.of());
        }, () -> securityAuditService.log(null, null, "PASSWORD_RESET_REQUESTED", "IGNORED", Map.of("emailHash", hash(email))));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        rateLimitService.check("reset-password-ip", securityAuditService.currentClientAddress(), 20, Duration.ofHours(1));
        AccountToken token = accountTokenService.consume(request.token(), AccountTokenType.PASSWORD_RESET);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        revokeAllForUser(user.getId());
        securityAuditService.log(user, user, "PASSWORD_RESET_COMPLETED", "SUCCESS", Map.of());
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        rateLimitService.check("verify-email-ip", securityAuditService.currentClientAddress(), 30, Duration.ofHours(1));
        AccountToken token = accountTokenService.consume(request.token(), AccountTokenType.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setEmailVerified(Boolean.TRUE);
        userRepository.save(user);
        securityAuditService.log(user, user, "EMAIL_VERIFIED", "SUCCESS", Map.of());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        AuthenticatedUser currentUser = currentUserService.getCurrentUser();
        return new CurrentUserResponse(
            currentUser.id(), currentUser.email(), currentUser.fullName(), currentUser.emailVerified(),
            currentUser.isActive(), currentUser.systemRole(), currentUser.lastLoginAt()
        );
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private AuthSession issueNewFamily(User user) {
        refreshTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        String rawToken = generateRefreshTokenValue();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000L);
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(hash(rawToken))
            .familyId(UUID.randomUUID())
            .expiresAt(expiresAt)
            .build());
        return new AuthSession(buildAuthResponse(user), rawToken, expiresAt);
    }

    private void sendVerificationEmail(User user) {
        String rawToken = accountTokenService.issue(
            user,
            AccountTokenType.EMAIL_VERIFICATION,
            Duration.ofMillis(emailVerificationTokenExpirationMs)
        );
        accountEmailSender.sendEmailVerification(user, rawToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        OffsetDateTime accessExpiresAt = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(jwtService.getAccessTokenExpiresAt().toEpochMilli()),
            OffsetDateTime.now().getOffset()
        );
        return new AuthResponse("Bearer", accessToken, accessExpiresAt,
            new AuthUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
                user.getEmailVerified(), user.getSystemRole()));
    }

    private void revokeFamily(UUID familyId, OffsetDateTime now) {
        List<RefreshToken> active = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
        active.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateRefreshTokenValue() {
        byte[] tokenBytes = new byte[48];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }
}
