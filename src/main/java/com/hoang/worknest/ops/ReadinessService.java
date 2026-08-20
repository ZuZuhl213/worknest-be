package com.hoang.worknest.ops;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReadinessService {
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public boolean isReady() {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return false;
            }
            try (var redis = redisTemplate.getConnectionFactory().getConnection()) {
                return "PONG".equalsIgnoreCase(redis.ping());
            }
        } catch (SQLException | RuntimeException ignored) {
            return false;
        }
    }
}
