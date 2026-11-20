-- V14: Add explicit cross_department_access authorization column to users
-- Replaces substring-based supervisor authorization with explicit server-side authorization

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cross_department_access BOOLEAN NOT NULL DEFAULT FALSE;

-- Grant explicit cross-department access to existing seeded central operations command supervisor
UPDATE users
SET cross_department_access = TRUE
WHERE email = 'sarah.jenkins@metro.gov';
