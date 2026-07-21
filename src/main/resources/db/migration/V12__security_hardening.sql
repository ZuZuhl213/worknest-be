ALTER TABLE users
    ADD COLUMN IF NOT EXISTS system_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS avatar_object_key TEXT;

ALTER TABLE users
    ADD CONSTRAINT chk_users_system_role CHECK (system_role IN ('USER', 'ADMIN'));

DO $$
BEGIN
    IF EXISTS (
        SELECT lower(email)
        FROM users
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce case-insensitive email uniqueness: duplicate emails exist';
    END IF;
END $$;

UPDATE users SET email = lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));

DROP TABLE IF EXISTS refresh_tokens CASCADE;
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id BIGINT REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE TABLE security_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_audit_logs_created_at ON security_audit_logs(created_at);
CREATE INDEX idx_security_audit_logs_action ON security_audit_logs(action);
