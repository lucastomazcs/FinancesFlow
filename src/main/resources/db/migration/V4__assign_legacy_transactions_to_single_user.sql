-- If the application has exactly one user, that account is the only possible owner
-- of transactions created before ownership was tracked.
UPDATE transactions
SET user_id = (SELECT id FROM app_users LIMIT 1)
WHERE user_id IS NULL
  AND (SELECT COUNT(*) FROM app_users) = 1;
