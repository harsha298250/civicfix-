-- ==============================================================================
-- CivicFix Platform — Migration V24: Add Firebase UID for Federated Authentication
-- Enables secure mapping of Firebase Email/Password identities to Neon PostgreSQL user accounts.
-- ==============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS firebase_uid VARCHAR(128) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users(firebase_uid);
