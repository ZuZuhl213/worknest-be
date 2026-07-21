# Security rollout runbook

1. Put the application in a maintenance window and back up PostgreSQL.
2. Run `psql "$DATABASE_URL" -v admin_email=admin@example.com -f scripts/security-preflight.sql`. Resolve every duplicate email and invalid workspace owner row.
3. Deploy Flyway migration V12, then grant the first administrator with:
   `psql "$DATABASE_URL" -v admin_email=admin@example.com -f scripts/grant-global-admin.sql`.
4. Deploy backend and frontend together with a newly generated JWT secret. Provide DB, Redis, S3 and JWT values only through the deployment secret manager. Production requires authenticated TLS Redis, reachable ClamAV, and an S3/R2 bucket with all public access blocked.
5. Remove all legacy refresh-token rows if migration execution was split, rotate any previously shared DB/AWS credentials, and flush the application Redis database. Do not restore an old JWT secret or old tokens during rollback.
6. Confirm login, CSRF cookie, refresh rotation, admin access and a clean upload. Monitor HTTP 401/403/429, refresh-token reuse, ClamAV errors, upload rejection and admin audit events.

The admin runbook increments `token_version`, revokes existing refresh tokens and writes an audit row. There is intentionally no API that promotes a user to global administrator.
