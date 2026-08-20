ALTER TABLE notifications ADD COLUMN deduplication_key VARCHAR(255);

UPDATE notifications
SET deduplication_key = 'overdue-task:' || substring(content FROM '^Task #([0-9]+) is overdue:')
WHERE title = 'Task overdue'
  AND content ~ '^Task #[0-9]+ is overdue:';

DELETE FROM notifications duplicate
USING notifications retained
WHERE duplicate.user_id = retained.user_id
  AND duplicate.deduplication_key = retained.deduplication_key
  AND duplicate.deduplication_key IS NOT NULL
  AND duplicate.id > retained.id;

CREATE UNIQUE INDEX uq_notifications_user_deduplication_key
    ON notifications (user_id, deduplication_key)
    WHERE deduplication_key IS NOT NULL;
