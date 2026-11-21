-- Flyway Migration V19: Add spatial boundary and ward validation metadata
-- Safe forward-only migration preserving all existing complaint records and versioning

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS ward_validation_status VARCHAR(50) DEFAULT 'NOT_EVALUATED' NOT NULL,
    ADD COLUMN IF NOT EXISTS ward_boundary_source VARCHAR(150),
    ADD COLUMN IF NOT EXISTS ward_boundary_distance_meters NUMERIC(10, 2);

CREATE INDEX IF NOT EXISTS idx_complaints_ward_validation
    ON complaints (ward_validation_status);
