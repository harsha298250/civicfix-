-- V13: Add security hardening fields
-- 1. Soft deletion fields on complaints
ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS deleted_by UUID NULL REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_complaints_deleted_at ON complaints(deleted_at);

-- 2. Officer cross-ward access authorization fields on users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cross_ward_access BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cross_ward_expires_at TIMESTAMP WITH TIME ZONE NULL;

-- 3. Persistent login attempt and lockout tracking across restarts
CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    ip_address VARCHAR(100) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 1,
    first_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_until TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uq_login_attempts_identifier_ip UNIQUE (identifier, ip_address)
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_identifier ON login_attempts(identifier);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON login_attempts(ip_address);
CREATE INDEX IF NOT EXISTS idx_login_attempts_locked_until ON login_attempts(locked_until);
