package com.hoang.worknest.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class CacheConfig {

    public static final String CURRENT_USER_WORKSPACES = "currentUserWorkspaces";
    public static final String WORKSPACE_DETAIL = "workspaceDetail";
    public static final String WORKSPACE_MEMBERS = "workspaceMembers";
    public static final String PROJECTS_BY_WORKSPACE = "projectsByWorkspace";
    public static final String PROJECT_DETAIL = "projectDetail";
    public static final String TASKS_BY_PROJECT = "tasksByProject";
    public static final String TASK_DETAIL = "taskDetail";

    @Bean
    @SuppressWarnings("null")
    public CacheManager cacheManager(@NonNull RedisConnectionFactory connectionFactory, @NonNull ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            CURRENT_USER_WORKSPACES, defaultConfig.entryTtl(Duration.ofMinutes(5)),
            WORKSPACE_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
            WORKSPACE_MEMBERS, defaultConfig.entryTtl(Duration.ofMinutes(5)),
            PROJECTS_BY_WORKSPACE, defaultConfig.entryTtl(Duration.ofMinutes(5)),
            PROJECT_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
            TASKS_BY_PROJECT, defaultConfig.entryTtl(Duration.ofMinutes(2)),
            TASK_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .build();
    }
}
