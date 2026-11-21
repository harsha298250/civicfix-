-- Flyway Migration V20: Add AI Prompt Governance and Versioning Metadata
-- Safe forward-only migration preserving all existing complaint records

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS vision_prompt_version VARCHAR(50) DEFAULT 'v1.1.0';

CREATE INDEX IF NOT EXISTS idx_complaints_vision_prompt_version
    ON complaints (vision_prompt_version);
