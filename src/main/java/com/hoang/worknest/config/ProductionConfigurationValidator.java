package com.hoang.worknest.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfigurationValidator {
    @Bean
    static Object productionConfigurationValidation(Environment environment) {
        validate(environment);
        return new Object();
    }

    static void validate(Environment environment) {
        List<String> invalid = new ArrayList<>();
        required(environment, invalid, "spring.datasource.url");
        required(environment, invalid, "spring.datasource.username");
        required(environment, invalid, "spring.datasource.password");
        required(environment, invalid, "spring.data.redis.url");
        required(environment, invalid, "app.jwt.secret");
        required(environment, invalid, "app.jwt.issuer");
        required(environment, invalid, "app.jwt.audience");
        required(environment, invalid, "aws.s3.region");
        required(environment, invalid, "aws.s3.access-key");
        required(environment, invalid, "aws.s3.secret-key");
        required(environment, invalid, "aws.s3.bucket-name");
        boolean emailEnabled = environment.getProperty("app.email.enabled", Boolean.class, false);
        if (emailEnabled) {
            required(environment, invalid, "spring.mail.host");
            required(environment, invalid, "spring.mail.username");
            required(environment, invalid, "spring.mail.password");
        }

        String frontend = environment.getProperty("app.frontend-base-url", "");
        if (!frontend.startsWith("https://")) {
            invalid.add("app.frontend-base-url");
        }
        if (!environment.getProperty("app.auth.cookie-secure", Boolean.class, true)) {
            invalid.add("app.auth.cookie-secure");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join(", ", invalid));
        }
    }

    private static void required(Environment environment, List<String> invalid, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.contains("${") || value.equalsIgnoreCase("change-me")
            || value.toLowerCase().contains("localhost")) {
            invalid.add(key);
        }
    }
}
