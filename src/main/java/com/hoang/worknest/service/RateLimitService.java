package com.hoang.worknest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.hoang.worknest.exception.TooManyRequestsException;
import com.hoang.worknest.exception.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
        "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return count",
        Long.class
    );
    private final StringRedisTemplate redisTemplate;
    private final SecurityAuditService auditService;

    public void check(String scope, String identity, int limit, Duration window) {
        String key = key(scope, identity);
        try {
            Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(window.toSeconds()));
            if (count == null) throw new IllegalStateException("Rate-limit counter did not return a value");
            if (count > limit) reject(scope, key, window);
        } catch (TooManyRequestsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable for rate-limit scope {}", scope);
            throw new ServiceUnavailableException("Authentication service temporarily unavailable", ex);
        }
    }

    public void checkCurrent(String scope, String identity, int limit, Duration window) {
        check(scope, identity, limit, window);
    }

    public void increment(String scope, String identity, Duration window) {
        check(scope, identity, Integer.MAX_VALUE, window);
    }

    public void reset(String scope, String identity) {
        try {
            redisTemplate.delete(key(scope, identity));
        } catch (RuntimeException ex) {
            throw new ServiceUnavailableException("Authentication service temporarily unavailable", ex);
        }
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
