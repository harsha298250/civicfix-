-- V16: Add isolated account-level and IP-level login attempt tracking tables
-- Provides concurrency-safe aggregate tracking and separates account failure reset from IP abuse history

CREATE TABLE IF NOT EXISTS account_login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 1,
    first_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_until TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uq_account_login_attempts_identifier UNIQUE (identifier)
);

CREATE INDEX IF NOT EXISTS idx_account_login_attempts_lower_id
    ON account_login_attempts (LOWER(identifier));
CREATE INDEX IF NOT EXISTS idx_account_login_attempts_locked_until
    ON account_login_attempts (locked_until);
CREATE INDEX IF NOT EXISTS idx_account_login_attempts_cleanup
    ON account_login_attempts (last_attempt_at, locked_until);

CREATE TABLE IF NOT EXISTS ip_login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address VARCHAR(100) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 1,
    first_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_until TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uq_ip_login_attempts_ip UNIQUE (ip_address)
);

CREATE INDEX IF NOT EXISTS idx_ip_login_attempts_ip
    ON ip_login_attempts (ip_address);
CREATE INDEX IF NOT EXISTS idx_ip_login_attempts_locked_until
    ON ip_login_attempts (locked_until);
CREATE INDEX IF NOT EXISTS idx_ip_login_attempts_cleanup
    ON ip_login_attempts (last_attempt_at, locked_until);
