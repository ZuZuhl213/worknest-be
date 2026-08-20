package com.hoang.worknest.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hoang.worknest.exception.ServiceUnavailableException;

class RateLimitServiceTest {

    @Test
    void rejectsAuthRequestsWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));

        RateLimitService service = new RateLimitService(redis, mock(SecurityAuditService.class));

        assertThrows(ServiceUnavailableException.class,
            () -> service.check("login", "192.0.2.1", 5, Duration.ofMinutes(1)));
    }
}
