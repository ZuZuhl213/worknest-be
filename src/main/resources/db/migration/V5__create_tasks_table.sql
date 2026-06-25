CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,

    project_id BIGINT NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'TODO',

    priority VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',

    due_date TIMESTAMP,

    created_by BIGINT NOT NULL,
    assigned_to BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_task_creator
        FOREIGN KEY (created_by)
        REFERENCES users(id),

    CONSTRAINT fk_task_assignee
        FOREIGN KEY (assigned_to)
        REFERENCES users(id)
);

CREATE INDEX idx_task_project
ON tasks(project_id);

CREATE INDEX idx_task_assigned
ON tasks(assigned_to);

CREATE INDEX idx_task_status
ON tasks(status);