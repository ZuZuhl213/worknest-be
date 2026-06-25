CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    task_number BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assignee_user_id BIGINT,
    reporter_user_id BIGINT NOT NULL,
    due_date TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tasks_project_task_number UNIQUE (project_id, task_number),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee_user
        FOREIGN KEY (assignee_user_id) REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_tasks_reporter_user
        FOREIGN KEY (reporter_user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_tasks_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT chk_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE')),
    CONSTRAINT chk_tasks_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_tasks_task_number_positive CHECK (task_number > 0),
    CONSTRAINT chk_tasks_completed_after_created
        CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE INDEX idx_tasks_project_id ON tasks (project_id);
CREATE INDEX idx_tasks_assignee_user_id ON tasks (assignee_user_id);
CREATE INDEX idx_tasks_reporter_user_id ON tasks (reporter_user_id);
CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
CREATE INDEX idx_tasks_project_priority ON tasks (project_id, priority);
CREATE INDEX idx_tasks_project_due_date ON tasks (project_id, due_date);
