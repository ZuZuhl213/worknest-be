-- Backfill a project LEAD for every project that has none.
--
-- Projects created before this migration never got a project_members row for their
-- creator, so their member list was empty and only workspace OWNER/ADMIN could manage
-- them (via the bypass in ProjectAuthorizationService). Promote the original creator
-- to LEAD so every project has at least one lead, which is what
-- ProjectMemberService.preventRemovingLastLead assumes.

INSERT INTO project_members (project_id, user_id, role, added_by_user_id, joined_at)
SELECT p.id, p.created_by_user_id, 'LEAD', p.created_by_user_id, p.created_at
FROM projects p
WHERE NOT EXISTS (
    SELECT 1 FROM project_members pm
    WHERE pm.project_id = p.id AND pm.role = 'LEAD'
)
ON CONFLICT (project_id, user_id) DO UPDATE SET role = 'LEAD'
WHERE NOT EXISTS (
    SELECT 1 FROM project_members pm
    WHERE pm.project_id = EXCLUDED.project_id AND pm.role = 'LEAD'
);
