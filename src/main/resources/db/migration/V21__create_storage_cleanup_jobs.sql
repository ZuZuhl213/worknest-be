CREATE TABLE storage_cleanup_jobs (
    id BIGSERIAL PRIMARY KEY,
    bucket_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_storage_cleanup_jobs_bucket_object UNIQUE (bucket_name, object_key),
    CONSTRAINT chk_storage_cleanup_jobs_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_storage_cleanup_jobs_next_attempt_at
    ON storage_cleanup_jobs (next_attempt_at);
