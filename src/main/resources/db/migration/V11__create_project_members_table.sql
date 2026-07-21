-- Project Members table
-- Stores project-level role assignments for workspace members.
-- Workspace ADMIN/OWNER bypass this table and have full access to all projects.

CREATE TABLE project_members (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    added_by_user_id BIGINT,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- A user can only have one role per project
    CONSTRAINT uq_project_members_project_user UNIQUE (project_id, user_id),

    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_members_added_by
        FOREIGN KEY (added_by_user_id) REFERENCES users (id)
        ON DELETE SET NULL,

    CONSTRAINT chk_project_members_role
        CHECK (role IN ('LEAD', 'MEMBER', 'VIEWER'))
);

CREATE INDEX idx_project_members_project_id ON project_members (project_id);
CREATE INDEX idx_project_members_user_id ON project_members (user_id);
CREATE INDEX idx_project_members_project_role ON project_members (project_id, role);
