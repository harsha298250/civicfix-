-- V15: Harden login security lockout indexes and atomic support
-- 1. Functional index on LOWER(identifier) for fast case-insensitive lookups
CREATE INDEX IF NOT EXISTS idx_login_attempts_lower_identifier
    ON login_attempts (LOWER(identifier));

-- 2. Composite index on LOWER(identifier) and last_attempt_at for fast account-wide aggregation
CREATE INDEX IF NOT EXISTS idx_login_attempts_id_last_attempt
    ON login_attempts (LOWER(identifier), last_attempt_at);

-- 3. Composite index on ip_address and last_attempt_at for fast IP-wide aggregation
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_last_attempt
    ON login_attempts (ip_address, last_attempt_at);

-- 4. Composite index for bounded cleanup of expired records preserving active lockouts
CREATE INDEX IF NOT EXISTS idx_login_attempts_cleanup
    ON login_attempts (last_attempt_at, locked_until);
