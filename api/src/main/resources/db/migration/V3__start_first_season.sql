-- RANK-07: initialize the first 4-week season so Ranked rewards have an active season from launch.
INSERT INTO season (starts_at, ends_at)
VALUES (now(), now() + interval '4 weeks');

-- ECO-07: email verification is a distinct, one-time Shard earning movement.
ALTER TABLE shard_transaction DROP CONSTRAINT chk_shard_reason_consistent;

ALTER TABLE shard_transaction ADD CONSTRAINT chk_shard_reason_consistent CHECK (
  (reason = 'PACK_PURCHASE' AND pack_opening_id IS NOT NULL AND invited_player_id IS NULL
    AND season_id IS NULL AND orbit IS NULL AND amount < 0)
  OR (reason = 'INVITE_BONUS' AND invited_player_id IS NOT NULL AND pack_opening_id IS NULL
    AND season_id IS NULL AND orbit IS NULL AND amount > 0)
  OR (reason = 'SEASON_REWARD' AND season_id IS NOT NULL AND pack_opening_id IS NULL
    AND invited_player_id IS NULL AND orbit IS NULL AND amount > 0)
  OR (reason = 'ORBIT_REWARD' AND season_id IS NOT NULL AND orbit IS NOT NULL
    AND pack_opening_id IS NULL AND invited_player_id IS NULL AND amount > 0)
  OR (reason = 'EMAIL_BONUS' AND pack_opening_id IS NULL AND invited_player_id IS NULL
    AND season_id IS NULL AND orbit IS NULL AND amount > 0)
  OR (reason IN ('REGISTRATION_BONUS', 'DAILY_BONUS', 'COMEBACK_BONUS')
    AND pack_opening_id IS NULL AND invited_player_id IS NULL AND season_id IS NULL
    AND orbit IS NULL AND amount > 0)
);

-- ECO-07: add EMAIL_BONUS to the transaction reason allow-list.
ALTER TABLE shard_transaction DROP CONSTRAINT shard_transaction_reason_check;

ALTER TABLE shard_transaction ADD CONSTRAINT shard_transaction_reason_check CHECK (
  reason IN (
    'REGISTRATION_BONUS', 'DAILY_BONUS', 'COMEBACK_BONUS', 'EMAIL_BONUS',
    'SEASON_REWARD', 'ORBIT_REWARD', 'INVITE_BONUS', 'PACK_PURCHASE'
  )
);

CREATE UNIQUE INDEX uq_shard_email_bonus
  ON shard_transaction (player_id)
  WHERE reason = 'EMAIL_BONUS';
