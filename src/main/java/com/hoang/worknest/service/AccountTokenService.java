package com.hoang.worknest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.entity.AccountToken;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.AccountTokenType;
import com.hoang.worknest.exception.InvalidAccountTokenException;
import com.hoang.worknest.repository.AccountTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AccountTokenRepository accountTokenRepository;

    @Transactional
    public String issue(User user, AccountTokenType type, Duration ttl) {
        accountTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        accountTokenRepository.deleteByUserIdAndTypeAndUsedAtIsNull(user.getId(), type);
        String rawToken = generateTokenValue();
        accountTokenRepository.save(AccountToken.builder()
            .user(user)
            .type(type)
            .tokenHash(hash(rawToken))
            .expiresAt(OffsetDateTime.now().plus(ttl))
            .build());
        return rawToken;
    }

    @Transactional
    public AccountToken consume(String rawToken, AccountTokenType expectedType) {
        AccountToken token = accountTokenRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new InvalidAccountTokenException("Token is invalid or expired"));
        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null
            || token.getType() != expectedType
            || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidAccountTokenException("Token is invalid or expired");
        }
        token.setUsedAt(now);
        return accountTokenRepository.save(token);
    }

    String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }

    private String generateTokenValue() {
        byte[] tokenBytes = new byte[48];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }
}
