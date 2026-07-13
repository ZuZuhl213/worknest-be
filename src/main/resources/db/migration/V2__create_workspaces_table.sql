CREATE TABLE workspaces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT,
    owner_user_id BIGINT NOT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_workspaces_slug UNIQUE (slug),
    CONSTRAINT fk_workspaces_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_workspaces_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_workspaces_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT chk_workspaces_slug_lowercase CHECK (slug = lower(slug))
);

CREATE INDEX idx_workspaces_owner_user_id ON workspaces (owner_user_id);
CREATE INDEX idx_workspaces_is_archived ON workspaces (is_archived);
