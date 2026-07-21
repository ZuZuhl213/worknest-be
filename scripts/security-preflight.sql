\set ON_ERROR_STOP on

-- Must return zero rows before V12 can create the case-insensitive unique index.
SELECT lower(trim(email)) AS normalized_email, count(*) AS duplicates
FROM users
GROUP BY lower(trim(email))
HAVING count(*) > 1;

-- Every workspace must have exactly one OWNER membership matching owner_user_id.
SELECT w.id, w.slug, w.owner_user_id,
       count(wm.id) FILTER (WHERE wm.role = 'OWNER') AS owner_memberships,
       count(wm.id) FILTER (WHERE wm.role = 'OWNER' AND wm.user_id = w.owner_user_id) AS matching_owner
FROM workspaces w
LEFT JOIN workspace_members wm ON wm.workspace_id = w.id
GROUP BY w.id, w.slug, w.owner_user_id
HAVING count(wm.id) FILTER (WHERE wm.role = 'OWNER') <> 1
    OR count(wm.id) FILTER (WHERE wm.role = 'OWNER' AND wm.user_id = w.owner_user_id) <> 1;

\if :{?admin_email}
  -- Must return one active user selected for the post-migration admin bootstrap.
  SELECT id, email, is_active
  FROM users
  WHERE lower(email) = lower(trim(:'admin_email')) AND is_active = true;
\else
  \echo 'Tip: pass -v admin_email=admin@example.com to validate the intended first administrator.'
\endif
