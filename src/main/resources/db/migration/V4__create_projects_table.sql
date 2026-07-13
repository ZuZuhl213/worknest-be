CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    description TEXT,
    created_by_user_id BIGINT NOT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_projects_workspace_project_key UNIQUE (workspace_id, project_key),
    CONSTRAINT uq_projects_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT fk_projects_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_projects_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_projects_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_projects_project_key_not_blank CHECK (btrim(project_key) <> ''),
    CONSTRAINT chk_projects_project_key_uppercase CHECK (project_key = upper(project_key))
);

CREATE INDEX idx_projects_workspace_id ON projects (workspace_id);
CREATE INDEX idx_projects_created_by_user_id ON projects (created_by_user_id);
CREATE INDEX idx_projects_workspace_archived ON projects (workspace_id, is_archived);
