CREATE TABLE workspace_members (
    id BIGSERIAL PRIMARY KEY,

    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    role VARCHAR(20) NOT NULL,

    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workspace_member_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_workspace_member_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_workspace_user
        UNIQUE(workspace_id, user_id)
);