package com.hoang.worknest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hoang.worknest.dto.auth.RegisterRequest;
import com.hoang.worknest.exception.InvalidRefreshTokenException;
import com.hoang.worknest.repository.RefreshTokenRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.service.AuthService;
import com.hoang.worknest.security.JwtService;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.SystemRole;

@SpringBootTest(properties = {
    "server.port=0",
    "app.jwt.secret=test-only-jwt-secret-with-at-least-32-bytes",
    "aws.s3.access-key=test",
    "aws.s3.secret-key=test",
    "aws.s3.region=us-east-1",
    "aws.s3.bucket-name=test",
    "security.clamav.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.show-sql=false"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class WorknestApplicationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("worknest")
        .withUsername("worknest")
        .withPassword("worknest");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void contextLoadsAgainstIsolatedPostgresAndRedis() {
    }

    @Test
    void registrationReturnsRefreshTokenOnlyAsHardenedCookie() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"a sufficiently long passphrase 1","fullName":"Cookie Test"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly("worknest_rt", true))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api/auth")))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void registrationAcceptsEightCharacterPasswordWithLettersAndNumbers() throws Exception {
        String email = "password-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"abcde123","fullName":"Password Test"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void registrationRejectsPasswordWithoutNumber() throws Exception {
        String email = "password-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"abcdefgh","fullName":"Password Test"}
                    """.formatted(email)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed: password: Password must contain at least one letter and one number"))
            .andExpect(jsonPath("$.fields.password").value("Password must contain at least one letter and one number"));
    }

    @Test
    void concurrentRefreshAllowsOneRotationAndRevokesFamilyOnReuse() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        AuthService.AuthSession initial = authService.register(
            new RegisterRequest(email, "another sufficiently long passphrase", "Refresh Test")
        );
        String rawToken = initial.refreshToken();
        var stored = refreshTokenRepository.findFirstByTokenHash(sha256(rawToken)).orElseThrow();
        assertNotEquals(rawToken, stored.getTokenHash());

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<AuthService.AuthSession> first = executor.submit(() -> {
                start.await();
                return authService.refresh(rawToken);
            });
            Future<AuthService.AuthSession> second = executor.submit(() -> {
                start.await();
                return authService.refresh(rawToken);
            });
            start.countDown();

            int success = 0;
            int reuseRejected = 0;
            for (Future<AuthService.AuthSession> result : java.util.List.of(first, second)) {
                try {
                    result.get();
                    success++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    if (ex.getCause() instanceof InvalidRefreshTokenException) reuseRejected++;
                    else throw ex;
                }
            }
            assertEquals(1, success);
            assertEquals(1, reuseRejected);
        }

        assertTrue(refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(stored.getFamilyId()).isEmpty());
    }

    @Test
    void onlyGlobalAdminCanAccessAdminUserApi() throws Exception {
        User regular = saveUser(SystemRole.USER);
        User admin = saveUser(SystemRole.ADMIN);

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(regular)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk());
    }

    private User saveUser(SystemRole role) {
        return userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
            .passwordHash("not-used-in-this-test")
            .fullName(role.name())
            .isActive(true)
            .emailVerified(true)
            .systemRole(role)
            .tokenVersion(0)
            .build());
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
