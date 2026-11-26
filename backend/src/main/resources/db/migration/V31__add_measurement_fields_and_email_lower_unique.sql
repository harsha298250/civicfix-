-- Enforce case-insensitive email uniqueness in PostgreSQL while preserving existing NULL emails
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower_unique
    ON users (LOWER(email))
    WHERE email IS NOT NULL;

-- Store AI/calibration-assisted defect estimates separately from citizen-confirmed approximate dimensions
ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS ai_estimated_length_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS ai_estimated_width_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS ai_measurement_confidence NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS calibration_reference_type VARCHAR(60),
    ADD COLUMN IF NOT EXISTS citizen_confirmed_length_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS citizen_confirmed_width_cm NUMERIC(8, 2);
