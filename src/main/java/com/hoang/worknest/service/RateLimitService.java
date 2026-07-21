package com.hoang.worknest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.hoang.worknest.exception.TooManyRequestsException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final SecurityAuditService auditService;

    public void check(String scope, String identity, int limit, Duration window) {
        checkCurrent(scope, identity, limit, window);
        increment(scope, identity, window);
    }

    public void checkCurrent(String scope, String identity, int limit, Duration window) {
        String key = key(scope, identity);
        String rawCount = redisTemplate.opsForValue().get(key);
        long count = rawCount == null ? 0 : Long.parseLong(rawCount);
        if (count >= limit) {
            reject(scope, key, window);
        }
    }

    public void increment(String scope, String identity, Duration window) {
        String key = key(scope, identity);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    public void reset(String scope, String identity) {
        redisTemplate.delete(key(scope, identity));
    }

    private void reject(String scope, String key, Duration window) {
        Long ttl = redisTemplate.getExpire(key);
        long retryAfter = ttl == null || ttl < 1 ? window.toSeconds() : ttl;
        auditService.log(null, null, "RATE_LIMIT_" + scope.toUpperCase(), "BLOCKED", java.util.Map.of());
        throw new TooManyRequestsException("Too many requests", retryAfter);
    }

    private String key(String scope, String identity) {
        return "rate-limit:" + scope + ":" + hash(identity == null ? "unknown" : identity);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash rate-limit key", ex);
        }
    }
}
