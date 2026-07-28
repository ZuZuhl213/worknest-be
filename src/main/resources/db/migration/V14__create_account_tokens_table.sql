CREATE TABLE account_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_tokens_type CHECK (type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'))
);

CREATE INDEX idx_account_tokens_user_type ON account_tokens(user_id, type);
CREATE INDEX idx_account_tokens_expires_at ON account_tokens(expires_at);
