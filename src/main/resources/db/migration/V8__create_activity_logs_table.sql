CREATE TABLE activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    project_id UUID,
    task_id UUID,
    actor_user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_activity_logs_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_activity_logs_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_activity_logs_task
        FOREIGN KEY (task_id) REFERENCES tasks (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_activity_logs_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_activity_logs_action_not_blank CHECK (btrim(action) <> ''),
    CONSTRAINT chk_activity_logs_entity_type_not_blank CHECK (btrim(entity_type) <> '')
);

CREATE INDEX idx_activity_logs_workspace_created_at ON activity_logs (workspace_id, created_at);
CREATE INDEX idx_activity_logs_task_created_at ON activity_logs (task_id, created_at);
CREATE INDEX idx_activity_logs_actor_user_id ON activity_logs (actor_user_id);
CREATE INDEX idx_activity_logs_entity_lookup ON activity_logs (entity_type, entity_id);
