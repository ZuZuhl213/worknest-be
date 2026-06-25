CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150),
    file_size BIGINT NOT NULL,
    bucket_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_attachments_object_key UNIQUE (object_key),
    CONSTRAINT fk_attachments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attachments_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_attachments_file_name_not_blank CHECK (btrim(file_name) <> ''),
    CONSTRAINT chk_attachments_bucket_name_not_blank CHECK (btrim(bucket_name) <> ''),
    CONSTRAINT chk_attachments_object_key_not_blank CHECK (btrim(object_key) <> ''),
    CONSTRAINT chk_attachments_file_size_non_negative CHECK (file_size >= 0)
);

CREATE INDEX idx_attachments_task_id ON attachments (task_id);
CREATE INDEX idx_attachments_uploaded_by_user_id ON attachments (uploaded_by_user_id);
