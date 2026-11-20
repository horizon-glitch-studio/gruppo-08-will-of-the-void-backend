-- Track authenticated player activity, throttling writes to at most once per hour.
ALTER TABLE player ADD COLUMN last_active_at timestamptz;
ALTER TABLE player ADD COLUMN comeback_eligible boolean NOT NULL DEFAULT false;

UPDATE player
SET last_active_at = COALESCE(last_daily_bonus_at, created_at);
