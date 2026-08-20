# Production Foundation Design

## Goal

Make the Spring Boot backend safe and predictable for production-like deployments without adding monitoring infrastructure or new application dependencies.

## Scope

- Make Flyway the sole mechanism for database schema evolution.
- Separate local and production configuration.
- Fail startup in the production profile when required configuration is missing or unsafe.
- Provide unauthenticated liveness and readiness endpoints with no sensitive detail.
- Correlate HTTP requests and logs with `X-Request-Id`.
- Rate-limit public authentication endpoints with Redis.
- Store Quartz jobs in PostgreSQL so scheduled work survives restart and multiple instances coordinate.
- Provide Docker Compose services for PostgreSQL, Redis, Mailpit, and MinIO.
- Add integration coverage with the already-installed Testcontainers PostgreSQL dependency.

## Non-Goals

- Prometheus, dashboards, tracing, log shipping, alerts, Kubernetes manifests, backup automation, and distributed queues.
- Changing business APIs, authorization rules, or frontend behavior.
- Adding a new rate-limit library or an Actuator dependency.

## Configuration Profiles

`application.yaml` contains shared settings only and no development defaults for security-sensitive production values.

`application-local.yaml` provides local-only defaults:

- `spring.jpa.hibernate.ddl-auto: validate`
- SQL logging enabled.
- HTTP-only refresh cookie may use `cookie-secure: false`.
- Quartz uses JDBC tables in the local PostgreSQL service.

`application-prod.yaml` enforces production values:

- `spring.jpa.hibernate.ddl-auto: validate`
- SQL and OpenAPI UI disabled.
- `app.auth.cookie-secure: true`.
- Quartz uses JDBC tables.

The active profile is explicit through `SPRING_PROFILES_ACTIVE`; local development uses `local` and deployments use `prod`.

## Schema Management

Hibernate must never create or mutate production schema. Flyway runs before JPA validation and reads migrations from `classpath:db/migration`.

The initial migration represents the current schema. Later schema changes are additive versioned migrations. Existing deployments must baseline deliberately through Flyway configuration rather than `ddl-auto=update`.

Quartz tables use the official PostgreSQL schema migration supplied as a project migration. Application-owned tables and Quartz tables are both managed by Flyway.

## Startup Validation

A production-only configuration validator runs before the application accepts requests. It rejects blank or placeholder values for:

- database URL, username, password
- Redis URL
- JWT secret, issuer, audience
- frontend base URL using HTTPS
- S3 bucket, endpoint, region, access key, and secret key when object storage is enabled
- SMTP host, username, and password when email is enabled

It rejects `cookie-secure: false` in production. Startup errors identify the configuration key only, never its value.

## Health Endpoints

`GET /health` returns `200 {"status":"UP"}` when the process can handle HTTP requests.

`GET /ready` checks PostgreSQL with a lightweight connection validation, Redis with `PING`, and S3 with a bucket metadata request only when object storage is configured. It returns:

- `200 {"status":"UP"}` when all required dependencies are available.
- `503 {"status":"DOWN"}` when any dependency is unavailable.

Responses never include hostnames, exception text, credentials, or stack traces. Both routes bypass JWT authentication but remain rate-limited only by deployment infrastructure, not application code.

## Request Correlation

A servlet filter accepts a valid incoming `X-Request-Id` consisting of 1-128 URL-safe characters. Invalid or absent input is replaced with a generated UUID.

The filter stores the request ID in MDC for the request lifetime, adds `X-Request-Id` to every response, and removes MDC state in `finally`. Application log patterns include `%X{requestId}`.

## Authentication Rate Limiting

The application uses the existing Redis connection directly. Rate limits apply before controller execution to:

- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/register`
- `POST /api/auth/refresh`
- password reset and email verification endpoints, if present

Keys combine normalized client IP and normalized email when an email is present. The client IP uses the first value in `X-Forwarded-For` only because `server.forward-headers-strategy=framework` is already enabled and deployment must restrict trusted proxies.

The initial policy is five attempts per minute for login, Google login, registration, and password reset; thirty refreshes per minute per IP. Redis uses atomic increment plus TTL. Exceeded requests return `429` with JSON error code `RATE_LIMITED` and a whole-second `Retry-After` header. Redis failures fail open but emit a warning with request ID, preventing an availability dependency from blocking login entirely.

## Quartz Persistence

Quartz uses the JDBC job store and PostgreSQL datasource. Instances derive their scheduler identity automatically and use database locking. The existing overdue task scheduler remains unchanged functionally; its state and triggers survive restart.

## Docker Compose

`compose.yaml` defines:

- PostgreSQL with a named volume and health check.
- Redis with a named volume and health check.
- Mailpit for local email inspection.
- MinIO with a named volume and a bucket initialization service.

It exposes only local development ports. Credentials are environment-configured in `.env.example`; `.env` remains ignored. The application itself may run locally or as a Compose service using identical environment names.

## Testing

- Flyway integration test starts PostgreSQL with Testcontainers and validates migrations plus JPA startup.
- Production config validation tests verify missing secrets and insecure cookies stop startup.
- MVC tests cover health/readiness status behavior and `X-Request-Id` response propagation.
- Rate-limit tests use a test Redis connection or mocked `StringRedisTemplate` to verify 429, `Retry-After`, and fail-open behavior.
- Quartz integration test verifies a persisted trigger remains available after scheduler recreation against PostgreSQL.

## Acceptance Criteria

- `prod` cannot start with unsafe cookie configuration or missing required production secrets.
- No profile uses Hibernate schema mutation.
- Fresh PostgreSQL starts through Flyway migrations and JPA validation.
- `/health` and `/ready` return only status and correct HTTP status.
- Every response contains one valid `X-Request-Id`; MDC does not leak across requests.
- Auth endpoint attempts above the configured budget return `429` and `Retry-After`.
- Quartz uses PostgreSQL-backed persistence.
- Local infrastructure starts with one Compose command.
