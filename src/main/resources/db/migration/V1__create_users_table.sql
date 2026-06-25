CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,

    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    full_name VARCHAR(120) NOT NULL,

    avatar_url TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email
ON users(email);
