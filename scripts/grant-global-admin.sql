\set ON_ERROR_STOP on
\if :{?admin_email}
\else
  \echo 'Usage: psql ... -v admin_email=admin@example.com -f scripts/grant-global-admin.sql'
  \quit 2
\endif

BEGIN;

SELECT (count(*) = 1) AS target_found, coalesce(min(id), 0) AS target_user_id
FROM users
WHERE lower(email) = lower(trim(:'admin_email'))
  AND is_active = true
\gset

\if :target_found
  SELECT id FROM users WHERE id = :target_user_id FOR UPDATE;
  UPDATE users
  SET system_role = 'ADMIN', token_version = token_version + 1
  WHERE id = :target_user_id;

  UPDATE refresh_tokens
  SET revoked_at = CURRENT_TIMESTAMP
  WHERE user_id = :target_user_id AND revoked_at IS NULL;

  INSERT INTO security_audit_logs(target_user_id, action, outcome, metadata)
  VALUES (
    :target_user_id,
    'SYSTEM_ROLE_GRANTED_BY_RUNBOOK',
    'SUCCESS',
    jsonb_build_object('role', 'ADMIN', 'operator', current_user)
  );
\else
  \echo 'No active user found for supplied email'
  ROLLBACK;
  \quit 3
\endif

COMMIT;
