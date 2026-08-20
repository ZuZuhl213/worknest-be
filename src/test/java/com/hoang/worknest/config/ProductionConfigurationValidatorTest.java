package com.hoang.worknest.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {
    @Test
    void rejectsInsecureCookieWithoutLeakingConfigurationValues() {
        var environment = new MockEnvironment()
            .withProperty("app.auth.cookie-secure", "false")
            .withProperty("app.jwt.secret", "super-secret-value");

        var error = assertThrows(IllegalStateException.class,
            () -> ProductionConfigurationValidator.validate(environment));

        assertTrue(error.getMessage().contains("app.auth.cookie-secure"));
        assertTrue(!error.getMessage().contains("super-secret-value"));
    }
}
