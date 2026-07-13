CREATE TABLE task_comments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_author_user
        FOREIGN KEY (author_user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_task_comments_content_not_blank CHECK (btrim(content) <> '')
);

CREATE INDEX idx_task_comments_task_id_created_at ON task_comments (task_id, created_at);
CREATE INDEX idx_task_comments_author_user_id ON task_comments (author_user_id);
