# Production Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the backend production-like through migration-owned schema, validated profiles, safe operational endpoints, request correlation, Redis auth throttling, persistent Quartz, and local infrastructure.

**Architecture:** Keep Spring Boot and its installed dependencies. Cross-cutting HTTP behavior lives in filters/controllers; configuration validation runs only under `prod`; Flyway owns schema and Quartz tables. Redis failure does not block authentication, while dependency readiness reports failure without leaking details.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Security, JPA/PostgreSQL, Flyway, Redis, Quartz, Testcontainers, Docker Compose.

## Global Constraints

- Do not add Actuator, Micrometer, a rate-limit library, or a queue.
- Do not expose secrets, internal hostnames, exception messages, or stack traces from health endpoints.
- Keep business APIs and authorization semantics unchanged.
- Use Flyway for all schema mutation; set Hibernate `ddl-auto` to `validate` in local and production profiles.
- Preserve existing user changes, including the deleted `docs/security-rollout.md`.

---

### Task 1: Flyway-Owned Schema and Runtime Profiles

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Create: `src/main/resources/application-local.yaml`
- Create: `src/main/resources/db/migration/V1__baseline_application_schema.sql`
- Create: `src/main/resources/db/migration/V2__create_quartz_tables.sql`
- Test: `src/test/java/com/hoang/worknest/FlywayStartupIntegrationTest.java`

**Interfaces:**
- Produces migrations under `classpath:db/migration` consumed by Flyway before JPA.
- Produces explicit `local` and `prod` profiles consumed through `SPRING_PROFILES_ACTIVE`.

- [ ] **Step 1: Export current PostgreSQL schema into a baseline migration**

Run against a schema matching the current application entities:

```bash
pg_dump --schema-only --no-owner --no-privileges "$DATABASE_URL" > src/main/resources/db/migration/V1__baseline_application_schema.sql
```

Remove `SET`, `SELECT pg_catalog.set_config`, extension ownership, and transaction control statements. Preserve tables, columns, constraints, indexes, and enum types.

- [ ] **Step 2: Add a failing Testcontainers startup test**

```java
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///worknest",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayStartupIntegrationTest {
    @Test void startsAgainstAnEmptyDatabaseUsingMigrations() {}
}
```

- [ ] **Step 3: Run the test and verify it fails before migrations/config change**

Run: `./mvnw -Dtest=FlywayStartupIntegrationTest test`

Expected: FAIL because an empty schema cannot pass JPA validation.

- [ ] **Step 4: Configure Flyway and remove Hibernate schema mutation**

Use shared configuration:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
```

Move local SQL logging and local insecure cookie configuration to `application-local.yaml`. Keep production SQL/OpenAPI disabled in `application-prod.yaml`.

- [ ] **Step 5: Add Quartz PostgreSQL schema as the second migration**

Copy the official Quartz PostgreSQL table definitions into `V2__create_quartz_tables.sql`. Do not hand-write a partial table set.

- [ ] **Step 6: Run migration startup verification**

Run: `./mvnw -Dtest=FlywayStartupIntegrationTest test`

Expected: PASS.

---

### Task 2: Production Configuration Validation

**Files:**
- Create: `src/main/java/com/hoang/worknest/config/ProductionConfigurationValidator.java`
- Modify: `src/main/resources/application-prod.yaml`
- Test: `src/test/java/com/hoang/worknest/config/ProductionConfigurationValidatorTest.java`

**Interfaces:**
- Consumes Spring `Environment` and `@ConfigurationProperties` values.
- Produces a production-only startup failure that lists invalid property keys, never property values.

- [ ] **Step 1: Write failing validation tests**

```java
@Test void rejectsAnInsecureRefreshCookieInProduction() {
    var environment = new MockEnvironment()
        .withProperty("spring.profiles.active", "prod")
        .withProperty("app.auth.cookie-secure", "false");

    assertThatThrownBy(() -> validator.validate(environment))
        .hasMessageContaining("app.auth.cookie-secure");
}

@Test void reportsMissingJwtSecretByKeyWithoutItsValue() { }
```

- [ ] **Step 2: Run the validator tests**

Run: `./mvnw -Dtest=ProductionConfigurationValidatorTest test`

Expected: FAIL because the validator does not exist.

- [ ] **Step 3: Implement `ProductionConfigurationValidator`**

Use `ApplicationRunner` with `@Profile("prod")`. Reject blank values, `change-me`, `localhost`, and local defaults where production requires external configuration. Validate DB, Redis, JWT secret/issuer/audience, HTTPS frontend URL, and enabled S3/SMTP settings. Never include resolved values in exceptions.

- [ ] **Step 4: Run validation tests**

Run: `./mvnw -Dtest=ProductionConfigurationValidatorTest test`

Expected: PASS.

---

### Task 3: Health, Readiness, and Request Correlation

**Files:**
- Create: `src/main/java/com/hoang/worknest/ops/HealthController.java`
- Create: `src/main/java/com/hoang/worknest/ops/ReadinessService.java`
- Create: `src/main/java/com/hoang/worknest/ops/RequestIdFilter.java`
- Modify: Spring Security configuration class after locating its actual path
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/hoang/worknest/ops/HealthControllerTest.java`
- Test: `src/test/java/com/hoang/worknest/ops/RequestIdFilterTest.java`

**Interfaces:**
- `GET /health` returns `200 {"status":"UP"}`.
- `GET /ready` returns `200 {"status":"UP"}` or `503 {"status":"DOWN"}`.
- `RequestIdFilter` returns valid `X-Request-Id` and places it in MDC during processing.

- [ ] **Step 1: Write failing MVC tests**

```java
mockMvc.perform(get("/health"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("UP"));

mockMvc.perform(get("/ready"))
    .andExpect(status().isServiceUnavailable())
    .andExpect(jsonPath("$.status").value("DOWN"));

mockMvc.perform(get("/health").header("X-Request-Id", "trace_123"))
    .andExpect(header().string("X-Request-Id", "trace_123"));
```

- [ ] **Step 2: Run the focused tests**

Run: `./mvnw -Dtest=HealthControllerTest,RequestIdFilterTest test`

Expected: FAIL because endpoints/filter do not exist.

- [ ] **Step 3: Implement status-only health/readiness**

`ReadinessService` calls `DataSource#getConnection().isValid(2)` and Redis `PING`. Call S3 `headBucket` only when storage is configured. Catch all dependency exceptions and return only `DOWN`; never serialize exception details.

- [ ] **Step 4: Implement `RequestIdFilter`**

Accept `[A-Za-z0-9_-]{1,128}`. Generate `UUID.randomUUID().toString()` otherwise. Put `requestId` in MDC, set response header, call `filterChain.doFilter`, and remove the MDC value in `finally`.

- [ ] **Step 5: Permit operational endpoints and add log correlation**

Permit only `/health` and `/ready` without authentication. Add `%X{requestId}` to the console logging pattern.

- [ ] **Step 6: Run focused tests**

Run: `./mvnw -Dtest=HealthControllerTest,RequestIdFilterTest test`

Expected: PASS.

---

### Task 4: Redis Authentication Rate Limiting

**Files:**
- Create: `src/main/java/com/hoang/worknest/security/AuthRateLimitFilter.java`
- Create: `src/main/java/com/hoang/worknest/security/AuthRateLimiter.java`
- Modify: Spring Security configuration class
- Test: `src/test/java/com/hoang/worknest/security/AuthRateLimitFilterTest.java`

**Interfaces:**
- Auth filter applies before auth controllers.
- `AuthRateLimiter#tryAcquire(String key, int limit, Duration window)` returns `RateLimitDecision(boolean allowed, long retryAfterSeconds)`.
- Exceeded requests return JSON `{"code":"RATE_LIMITED"}` with status `429` and `Retry-After`.

- [ ] **Step 1: Write failing filter tests**

```java
@Test void returns429AndRetryAfterAfterFiveLoginAttempts() throws Exception { }

@Test void allowsLoginWhenRedisIsUnavailable() throws Exception { }

@Test void ignoresNonAuthenticationPaths() throws Exception { }
```

- [ ] **Step 2: Run the rate-limit tests**

Run: `./mvnw -Dtest=AuthRateLimitFilterTest test`

Expected: FAIL because filter and limiter do not exist.

- [ ] **Step 3: Implement atomic Redis counter**

Use `StringRedisTemplate.opsForValue().increment(key)` and set expiration only on count one. Store keys as `ratelimit:auth:<route>:<ip>:<email-or-dash>`. Limit login, Google login, register, reset, and verification routes to five per minute; refresh to thirty per minute per IP.

- [ ] **Step 4: Implement the filter**

Read the first `X-Forwarded-For` entry or remote address. Read email only from JSON bodies that contain `email`; do not consume controller request bodies without a request wrapper. Normalize trim/lowercase email. On Redis exceptions, log a warning and proceed.

- [ ] **Step 5: Register the filter before username/password authentication**

Use `addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)`.

- [ ] **Step 6: Run rate-limit tests**

Run: `./mvnw -Dtest=AuthRateLimitFilterTest test`

Expected: PASS.

---

### Task 5: Persistent Quartz and Local Compose Infrastructure

**Files:**
- Modify: `src/main/resources/application-local.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Create: `compose.yaml`
- Create: `.env.example`
- Modify: `.gitignore`
- Test: `src/test/java/com/hoang/worknest/config/QuartzPersistenceIntegrationTest.java`

**Interfaces:**
- Quartz uses JDBC JobStore through the application PostgreSQL datasource.
- Compose exports `DB_*`, `REDIS_URL`, `SMTP_*`, and `S3_*` names already consumed by the application.

- [ ] **Step 1: Write a failing Quartz persistence test**

```java
@Test void storesScheduledJobsInTheJdbcJobStore() throws Exception {
    assertThat(scheduler.getMetaData().getJobStoreClass().getName())
        .contains("JobStoreTX");
}
```

- [ ] **Step 2: Run the Quartz test**

Run: `./mvnw -Dtest=QuartzPersistenceIntegrationTest test`

Expected: FAIL because the current configuration uses memory storage.

- [ ] **Step 3: Configure Quartz JDBC job storage**

Use `spring.quartz.job-store-type=jdbc`, `org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX`, PostgreSQL delegate, `isClustered=true`, and `instanceId=AUTO`. Keep schema initialization disabled because Flyway owns the tables.

- [ ] **Step 4: Create `compose.yaml` and `.env.example`**

Define PostgreSQL, Redis, Mailpit, MinIO, and one idempotent MinIO bucket initializer. Give PostgreSQL/Redis health checks, named volumes, local-only ports, and no hard-coded production credentials. Add `.env` to `.gitignore`.

- [ ] **Step 5: Run Quartz and Compose verification**

Run: `./mvnw -Dtest=QuartzPersistenceIntegrationTest test`

Expected: PASS.

Run: `docker compose config`

Expected: valid resolved Compose configuration.

---

### Task 6: Full Production Foundation Verification

**Files:**
- Modify: tests created in Tasks 1-5 only when a failing verification exposes a real defect.

**Interfaces:**
- Consumes all production foundation components from Tasks 1-5.
- Produces a clean Maven test suite and validated Compose definition.

- [ ] **Step 1: Run the full backend test suite**

Run: `./mvnw test`

Expected: PASS.

- [ ] **Step 2: Run a production configuration smoke test**

Run with production-safe test environment values for DB, Redis, JWT, frontend URL, S3, SMTP, and `AUTH_COOKIE_SECURE=true`:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Expected: application starts only with complete safe configuration.

- [ ] **Step 3: Verify failure is safe**

Run with `AUTH_COOKIE_SECURE=false` in the `prod` profile.

Expected: startup fails, output names `app.auth.cookie-secure`, and output does not contain any secret value.

- [ ] **Step 4: Verify local infrastructure definition**

Run: `docker compose config`

Expected: PASS.

- [ ] **Step 5: Inspect final changes**

Run: `git diff --check`

Expected: no whitespace errors.
