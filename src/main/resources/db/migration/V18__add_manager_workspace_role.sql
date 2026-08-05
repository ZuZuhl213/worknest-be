ALTER TABLE workspace_members
    DROP CONSTRAINT chk_workspace_members_role;

ALTER TABLE workspace_members
    ADD CONSTRAINT chk_workspace_members_role
    CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'MEMBER'));
