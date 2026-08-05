ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_system_role;

UPDATE users SET system_role = 'SYSTEM_ADMIN' WHERE system_role = 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT chk_users_system_role CHECK (system_role IN ('USER', 'SYSTEM_ADMIN'));

CREATE INDEX IF NOT EXISTS idx_users_created_at_desc ON users(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_last_login_at_desc ON users(last_login_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_active_created_at_desc ON users(is_active, created_at DESC);
