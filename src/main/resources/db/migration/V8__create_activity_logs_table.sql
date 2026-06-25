CREATE TABLE activity_logs (
    id BIGSERIAL PRIMARY KEY,

    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,

    action VARCHAR(50) NOT NULL,

    performed_by BIGINT NOT NULL,

    metadata JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activity_user
        FOREIGN KEY (performed_by)
        REFERENCES users(id)
);