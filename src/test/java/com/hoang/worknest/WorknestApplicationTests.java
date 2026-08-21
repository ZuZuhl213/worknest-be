package com.hoang.worknest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import com.hoang.worknest.dto.auth.ForgotPasswordRequest;
import com.hoang.worknest.dto.auth.LoginRequest;
import com.hoang.worknest.dto.auth.RegisterRequest;
import com.hoang.worknest.dto.auth.ResetPasswordRequest;
import com.hoang.worknest.dto.auth.VerifyEmailRequest;
import com.hoang.worknest.exception.InvalidAccountTokenException;
import com.hoang.worknest.exception.InvalidRefreshTokenException;
import com.hoang.worknest.repository.ActivityLogRepository;
import com.hoang.worknest.repository.AttachmentRepository;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.ProjectRepository;
import com.hoang.worknest.repository.RefreshTokenRepository;
import com.hoang.worknest.repository.TaskCommentRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.repository.WorkspaceRepository;
import com.hoang.worknest.service.AccountEmailSender;
import com.hoang.worknest.service.AccountTokenService;
import com.hoang.worknest.service.AuthService;
import com.hoang.worknest.service.FileStorageService;
import com.hoang.worknest.service.SecurityAuditService;
import com.hoang.worknest.security.JwtService;
import com.hoang.worknest.security.GoogleIdentityVerifier;
import com.hoang.worknest.security.GoogleIdentityVerifier.GoogleIdentity;
import com.hoang.worknest.entity.ActivityLog;
import com.hoang.worknest.entity.Attachment;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.TaskComment;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.entity.WorkspaceMember;
import com.hoang.worknest.enums.AccountTokenType;
import com.hoang.worknest.enums.ProjectRole;
import com.hoang.worknest.enums.WorkspaceRole;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.exception.TooManyRequestsException;

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
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Scheduler scheduler;
    @Autowired AccountTokenService accountTokenService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemberRepository projectMemberRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskCommentRepository taskCommentRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired ActivityLogRepository activityLogRepository;
    @Autowired SecurityAuditService securityAuditService;

    @MockitoBean AccountEmailSender accountEmailSender;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean GoogleIdentityVerifier googleIdentityVerifier;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void resetRateLimits() {
        Set<String> keys = redisTemplate.keys("rate-limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        reset(accountEmailSender);
        reset(fileStorageService);
        reset(googleIdentityVerifier);
    }

    @Test
    void contextLoadsAgainstIsolatedPostgresAndRedis() {
    }

    @Test
    void csrfEndpointIssuesPlainBodyToken() throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andReturn();

        String bodyToken = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("token")
            .asText();

        assertFalse(bodyToken.isBlank());
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"missing@example.com","password":"password123"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("CSRF token is missing. Refresh the page and try again."));
    }

    @Test
    void quartzPersistsScheduledJobsInPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JobKey jobKey = new JobKey("test-job-" + suffix, "test");
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("test-trigger-" + suffix, "test")
            .forJob(jobKey)
            .startAt(java.util.Date.from(java.time.Instant.now().plusSeconds(3600)))
            .build();

        try {
            scheduler.scheduleJob(JobBuilder.newJob(NoOpJob.class).withIdentity(jobKey).storeDurably().build(), trigger);

            assertTrue(scheduler.checkExists(jobKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from qrtz_job_details where job_name = ? and job_group = ?", Integer.class,
                jobKey.getName(), jobKey.getGroup()));
            assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from qrtz_triggers where trigger_name = ? and trigger_group = ?", Integer.class,
                trigger.getKey().getName(), trigger.getKey().getGroup()));
        } finally {
            scheduler.unscheduleJob(trigger.getKey());
            scheduler.deleteJob(jobKey);
        }
    }

    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    @Test
    void registrationRequiresEmailVerificationBeforeCreatingSession() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"a sufficiently long passphrase 1","fullName":"Cookie Test"}
                    """.formatted(email)))
            .andExpect(status().isAccepted())
            .andExpect(cookie().doesNotExist("worknest_rt"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(user.getEmailVerified());
        assertEquals(SystemRole.USER, user.getSystemRole());
        assertFalse(user.getCanCreateWorkspace());
        verify(accountEmailSender).sendEmailVerification(any(User.class), any(String.class));
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
            .andExpect(status().isAccepted())
            .andExpect(cookie().doesNotExist("worknest_rt"));
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
        AuthService.AuthSession initial = registerVerifyAndLogin(
            email, "another sufficiently long passphrase", "Refresh Test"
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
        User admin = saveUser(SystemRole.SYSTEM_ADMIN);
        User workspaceAdmin = saveUser(SystemRole.USER);
        User owner = saveUser(SystemRole.USER);
        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name("Admin check " + UUID.randomUUID())
            .slug("admin-check-" + UUID.randomUUID())
            .owner(owner)
            .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(workspaceAdmin)
            .role(WorkspaceRole.ADMIN)
            .joinedAt(OffsetDateTime.now())
            .build());

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(regular)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(workspaceAdmin)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/{id}/workspace-creation/enable", owner.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(regular)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk());
    }

    @Test
    void systemAdminCanFilterDisableAndInvalidateAnAccountToken() throws Exception {
        User admin = saveUser(SystemRole.SYSTEM_ADMIN);
        User target = saveUser(SystemRole.USER);
        target.setEmailVerified(false);
        target = userRepository.save(target);
        String targetToken = jwtService.generateAccessToken(target);

        mockMvc.perform(get("/api/admin/users")
                .param("search", target.getEmail())
                .param("active", "true")
                .param("emailVerified", "false")
                .param("role", "USER")
                .param("sort", "fullName")
                .param("direction", "asc")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(target.getId()));

        mockMvc.perform(post("/api/admin/users/{id}/disable", target.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));
        assertEquals(1, userRepository.findById(target.getId()).orElseThrow().getTokenVersion());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + targetToken))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/security-audit-logs")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.hasItem("ACCOUNT_DISABLED")));
        mockMvc.perform(post("/api/admin/users/{id}/disable", admin.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminCannotAccessWorkspaceWithoutMembership() throws Exception {
        User admin = saveUser(SystemRole.SYSTEM_ADMIN);
        User owner = saveUser(SystemRole.USER);
        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name("Global admin " + UUID.randomUUID())
            .slug("global-admin-" + UUID.randomUUID())
            .owner(owner)
            .archived(false)
            .build());

        mockMvc.perform(get("/api/workspaces/{id}", workspace.getId())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminCanEnableAndDisableWorkspaceCreation() throws Exception {
        User admin = saveUser(SystemRole.SYSTEM_ADMIN);
        User target = saveUser(SystemRole.USER);

        mockMvc.perform(post("/api/admin/users/{id}/workspace-creation/enable", target.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canCreateWorkspace").value(true));

        mockMvc.perform(post("/api/admin/users/{id}/workspace-creation/disable", target.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canCreateWorkspace").value(false));

        mockMvc.perform(get("/api/admin/security-audit-logs")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.hasItems(
                "WORKSPACE_CREATION_ENABLED", "WORKSPACE_CREATION_DISABLED"
            )));
    }

    @Test
    void workspaceOwnerCanDeleteWorkspace() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);

        mockMvc.perform(delete("/api/workspaces/{id}", fixture.workspace().getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.owner())))
            .andExpect(status().isNoContent());

        assertTrue(workspaceRepository.findById(fixture.workspace().getId()).isEmpty());
    }

    @Test
    void workspaceCreationRequiresEntitlementAndCountsArchivedWorkspaces() throws Exception {
        User unentitled = saveUser(SystemRole.USER);
        mockMvc.perform(post("/api/workspaces")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(workspaceCreateJson("unentitled"))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(unentitled)))
            .andExpect(status().isForbidden());

        User owner = saveUser(SystemRole.USER);
        owner.setCanCreateWorkspace(true);
        userRepository.save(owner);
        mockMvc.perform(post("/api/workspaces")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(workspaceCreateJson("entitled"))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(owner)))
            .andExpect(status().isCreated());
        Workspace created = workspaceRepository.findBySlug("entitled").orElseThrow();
        assertEquals(owner.getId(), created.getOwner().getId());
        assertEquals(WorkspaceRole.OWNER, workspaceMemberRepository
            .findByWorkspaceIdAndUserId(created.getId(), owner.getId()).orElseThrow().getRole());

        for (int index = 0; index < 4; index++) {
            workspaceRepository.save(Workspace.builder()
                .name("Archived " + index)
                .slug("archived-" + index)
                .owner(owner)
                .archived(true)
                .build());
        }
        mockMvc.perform(post("/api/workspaces")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(workspaceCreateJson("sixth"))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(owner)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Workspace creation limit of 5 has been reached"));

        User systemAdmin = saveUser(SystemRole.SYSTEM_ADMIN);
        systemAdmin.setCanCreateWorkspace(true);
        userRepository.save(systemAdmin);
        mockMvc.perform(post("/api/workspaces")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(workspaceCreateJson("system-admin"))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(systemAdmin)))
            .andExpect(status().isForbidden());
    }

    @Test
    void passwordResetTokenCanBeUsedOnlyOnceAndInvalidatesOldAccessToken() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        AuthService.AuthSession initial = registerVerifyAndLogin(
            email, "another sufficiently long passphrase 1", "Reset Test"
        );
        reset(accountEmailSender);

        authService.forgotPassword(new ForgotPasswordRequest(email));
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountEmailSender).sendPasswordReset(any(User.class), tokenCaptor.capture());

        authService.resetPassword(new ResetPasswordRequest(tokenCaptor.getValue(), "newpass123"));

        assertThrows(InvalidAccountTokenException.class,
            () -> authService.resetPassword(new ResetPasswordRequest(tokenCaptor.getValue(), "newpass123")));
        assertEquals(1, userRepository.findByEmailIgnoreCase(email).orElseThrow().getTokenVersion());
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + initial.response().accessToken()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredPasswordResetTokenFails() {
        User user = saveUser(SystemRole.USER);
        String rawToken = accountTokenService.issue(
            user,
            AccountTokenType.PASSWORD_RESET,
            java.time.Duration.ofMillis(-1)
        );

        assertThrows(InvalidAccountTokenException.class,
            () -> authService.resetPassword(new ResetPasswordRequest(rawToken, "newpass123")));
    }

    @Test
    void emailVerificationChangesEmailVerified() {
        User user = saveUnverifiedUser();
        String rawToken = accountTokenService.issue(
            user,
            AccountTokenType.EMAIL_VERIFICATION,
            java.time.Duration.ofHours(1)
        );

        authService.verifyEmail(new VerifyEmailRequest(rawToken));

        assertTrue(userRepository.findById(user.getId()).orElseThrow().getEmailVerified());
    }

    @Test
    void unverifiedUserCannotLogIn() throws Exception {
        String email = "unverified-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", "Unverified Test"));

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist("worknest_rt"))
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void resendVerificationInvalidatesPreviousToken() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", "Resend Test"));
        ArgumentCaptor<String> firstToken = ArgumentCaptor.forClass(String.class);
        verify(accountEmailSender).sendEmailVerification(any(User.class), firstToken.capture());
        reset(accountEmailSender);

        mockMvc.perform(post("/api/auth/resend-verification")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s"}
                    """.formatted(email)))
            .andExpect(status().isNoContent());

        ArgumentCaptor<String> secondToken = ArgumentCaptor.forClass(String.class);
        verify(accountEmailSender).sendEmailVerification(any(User.class), secondToken.capture());
        assertNotEquals(firstToken.getValue(), secondToken.getValue());
        assertThrows(InvalidAccountTokenException.class,
            () -> authService.verifyEmail(new VerifyEmailRequest(firstToken.getValue())));
        authService.verifyEmail(new VerifyEmailRequest(secondToken.getValue()));
    }

    @Test
    void googleLoginCreatesVerifiedUserAndHardenedSession() throws Exception {
        String email = "google-" + UUID.randomUUID() + "@example.com";
        when(googleIdentityVerifier.verify("valid-google-token"))
            .thenReturn(new GoogleIdentity("google-subject-" + UUID.randomUUID(), email, "Google User", "https://example.com/avatar.png"));

        mockMvc.perform(post("/api/auth/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"credential":"valid-google-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("worknest_rt", true))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.emailVerified").value(true));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(user.getEmailVerified());
        assertTrue(user.getGoogleSubject().startsWith("google-subject-"));
        assertEquals(SystemRole.USER, user.getSystemRole());
        assertFalse(user.getCanCreateWorkspace());
    }

    @Test
    void forgotPasswordRateLimitBlocksExcessiveAttempts() {
        User user = saveUser(SystemRole.USER);
        ForgotPasswordRequest request = new ForgotPasswordRequest(user.getEmail());

        authService.forgotPassword(request);
        authService.forgotPassword(request);
        authService.forgotPassword(request);

        assertThrows(TooManyRequestsException.class, () -> authService.forgotPassword(request));
    }

    @Test
    void authorizedTaskWriterCanDeleteAttachment() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);
        Task task = saveTask(fixture.project(), fixture.member());
        Attachment attachment = saveAttachment(task, fixture.member(), "clean/tasks/%d/delete-me.pdf".formatted(task.getId()));

        mockMvc.perform(delete("/api/tasks/{taskId}/attachments/{attachmentId}", task.getId(), attachment.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isNoContent());

        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from storage_cleanup_jobs where bucket_name = ? and object_key = ?",
            Integer.class, attachment.getBucketName(), attachment.getObjectKey()
        ));
        assertFalse(attachmentRepository.findById(attachment.getId()).isPresent());
    }

    @Test
    void unauthorizedTaskViewerCannotDeleteAttachment() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.VIEWER);
        Task task = saveTask(fixture.project(), fixture.owner());
        Attachment attachment = saveAttachment(task, fixture.owner(), "clean/tasks/%d/view-only.pdf".formatted(task.getId()));

        mockMvc.perform(delete("/api/tasks/{taskId}/attachments/{attachmentId}", task.getId(), attachment.getId())
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isForbidden());

        assertTrue(attachmentRepository.findById(attachment.getId()).isPresent());
    }

    @Test
    void deletingTaskRemovesAttachmentObjectsAndRows() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);
        Task task = saveTask(fixture.project(), fixture.member());
        Attachment attachment = saveAttachment(task, fixture.member(), "clean/tasks/%d/task-delete.pdf".formatted(task.getId()));

        mockMvc.perform(delete(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}",
                    fixture.workspace().getId(),
                    fixture.project().getId(),
                    task.getId()
                )
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isNoContent());

        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from storage_cleanup_jobs where bucket_name = ? and object_key = ?",
            Integer.class, attachment.getBucketName(), attachment.getObjectKey()
        ));
        assertTrue(attachmentRepository.findByTaskId(task.getId()).isEmpty());
    }

    @Test
    void projectAndTaskActivityRequireProjectAccess() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);
        Task task = saveTask(fixture.project(), fixture.member());
        activityLogRepository.save(ActivityLog.builder()
            .workspace(fixture.workspace())
            .project(fixture.project())
            .task(task)
            .actor(fixture.member())
            .action("TASK_UPDATED")
            .entityType("TASK")
            .entityId(task.getId())
            .details("{\"status\":\"TODO\"}")
            .build());

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activity",
                    fixture.workspace().getId(),
                    fixture.project().getId()
                )
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].action").value("TASK_UPDATED"))
            .andExpect(jsonPath("$.content[0].details.status").value("TODO"));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/activity",
                    fixture.workspace().getId(),
                    fixture.project().getId(),
                    task.getId()
                )
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].taskId").value(task.getId()));

        User outsider = saveUser(SystemRole.USER);
        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activity",
                    fixture.workspace().getId(),
                    fixture.project().getId()
                )
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(outsider)))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminCanReadSecurityAuditLogsAndRegularUserCannot() throws Exception {
        User regular = saveUser(SystemRole.USER);
        User admin = saveUser(SystemRole.SYSTEM_ADMIN);
        securityAuditService.log(admin, regular, "ACCOUNT_ENABLED", "SUCCESS", Map.of("source", "test"));

        mockMvc.perform(get("/api/admin/security-audit-logs")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.hasItem("ACCOUNT_ENABLED")))
            .andExpect(jsonPath("$.content[*].metadata.source").value(org.hamcrest.Matchers.hasItem("test")));

        mockMvc.perform(get("/api/admin/security-audit-logs")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(regular)))
            .andExpect(status().isForbidden());
    }

    @Test
    void paginatesCommentsAndMemberLists() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);
        Task task = saveTask(fixture.project(), fixture.member());
        taskCommentRepository.save(TaskComment.builder().task(task).author(fixture.member()).content("First").build());
        taskCommentRepository.save(TaskComment.builder().task(task).author(fixture.member()).content("Second").build());

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/comments",
                    fixture.workspace().getId(),
                    fixture.project().getId(),
                    task.getId()
                )
                .param("page", "0")
                .param("size", "1")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", fixture.workspace().getId())
                .param("size", "1")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/members",
                    fixture.workspace().getId(),
                    fixture.project().getId()
                )
                .param("size", "1")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void workspaceTasksRespectProjectAccessAssigneeFilterAndPagination() throws Exception {
        ProjectFixture fixture = createProjectFixture(ProjectRole.MEMBER);
        Task accessibleTask = saveTask(fixture.project(), fixture.owner(), fixture.member(), 1L);
        Project privateProject = saveProject(fixture.workspace(), fixture.owner());
        saveProjectMember(privateProject, fixture.owner(), ProjectRole.LEAD);
        saveTask(privateProject, fixture.owner(), fixture.member(), 1L);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/tasks", fixture.workspace().getId())
                .param("assigneeId", fixture.member().getId().toString())
                .param("size", "1")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(fixture.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(accessibleTask.getId()))
            .andExpect(jsonPath("$.content[0].projectName").value(fixture.project().getName()));
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

    private String workspaceCreateJson(String slug) {
        return """
            {"name":"%s","slug":"%s","description":"Test workspace"}
            """.formatted("Workspace " + slug, slug);
    }

    private ProjectFixture createProjectFixture(ProjectRole memberRole) {
        User owner = saveUser(SystemRole.USER);
        User member = saveUser(SystemRole.USER);
        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name("Workspace " + UUID.randomUUID())
            .slug("workspace-" + UUID.randomUUID())
            .owner(owner)
            .archived(false)
            .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(owner)
            .role(WorkspaceRole.OWNER)
            .joinedAt(OffsetDateTime.now())
            .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(member)
            .role(WorkspaceRole.MEMBER)
            .joinedAt(OffsetDateTime.now())
            .build());
        Project project = projectRepository.save(Project.builder()
            .workspace(workspace)
            .name("Project " + UUID.randomUUID())
            .projectKey("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
            .createdBy(owner)
            .archived(false)
            .build());
        projectMemberRepository.save(ProjectMember.builder()
            .project(project)
            .user(owner)
            .role(ProjectRole.LEAD)
            .addedBy(owner)
            .joinedAt(OffsetDateTime.now())
            .build());
        projectMemberRepository.save(ProjectMember.builder()
            .project(project)
            .user(member)
            .role(memberRole)
            .addedBy(owner)
            .joinedAt(OffsetDateTime.now())
            .build());
        return new ProjectFixture(workspace, project, owner, member);
    }

    private Task saveTask(Project project, User reporter) {
        return saveTask(project, reporter, null, 1L);
    }

    private Task saveTask(Project project, User reporter, User assignee, Long taskNumber) {
        return taskRepository.save(Task.builder()
            .project(project)
            .taskNumber(taskNumber)
            .title("Task " + UUID.randomUUID())
            .status(TaskStatus.TODO)
            .priority(TaskPriority.MEDIUM)
            .reporter(reporter)
            .assignee(assignee)
            .build());
    }

    private Project saveProject(Workspace workspace, User owner) {
        return projectRepository.save(Project.builder()
            .workspace(workspace)
            .name("Project " + UUID.randomUUID())
            .projectKey("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
            .createdBy(owner)
            .archived(false)
            .build());
    }

    private ProjectMember saveProjectMember(Project project, User user, ProjectRole role) {
        return projectMemberRepository.save(ProjectMember.builder()
            .project(project)
            .user(user)
            .role(role)
            .addedBy(project.getCreatedBy())
            .joinedAt(OffsetDateTime.now())
            .build());
    }

    private Attachment saveAttachment(Task task, User uploader, String objectKey) {
        return attachmentRepository.save(Attachment.builder()
            .task(task)
            .uploadedBy(uploader)
            .fileName("attachment.pdf")
            .contentType("application/pdf")
            .fileSize(12L)
            .bucketName("test")
            .objectKey(objectKey)
            .build());
    }

    private record ProjectFixture(Workspace workspace, Project project, User owner, User member) {
    }

    private User saveUnverifiedUser() {
        return userRepository.save(User.builder()
            .email("verify-" + UUID.randomUUID() + "@example.com")
            .passwordHash("not-used-in-this-test")
            .fullName("Verify Test")
            .isActive(true)
            .emailVerified(false)
            .systemRole(SystemRole.USER)
            .tokenVersion(0)
            .build());
    }

    private AuthService.AuthSession registerVerifyAndLogin(String email, String password, String fullName) {
        authService.register(new RegisterRequest(email, password, fullName));
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountEmailSender).sendEmailVerification(any(User.class), tokenCaptor.capture());
        authService.verifyEmail(new VerifyEmailRequest(tokenCaptor.getValue()));
        return authService.login(new LoginRequest(email, password));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
