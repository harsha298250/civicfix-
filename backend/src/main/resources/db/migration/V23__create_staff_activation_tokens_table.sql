-- ==============================================================================
-- CivicFix Database Migration: V23
-- Secure Staff Onboarding: Staff Activation Tokens & SLA Warning Metadata
-- ==============================================================================

CREATE TABLE IF NOT EXISTS staff_activation_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_admin_id UUID REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_staff_activation_tokens_hash ON staff_activation_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_staff_activation_tokens_user_id ON staff_activation_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_staff_activation_tokens_expires_at ON staff_activation_tokens(expires_at);

-- Add SLA approaching-deadline warning tracking column to complaints table
ALTER TABLE complaints ADD COLUMN IF NOT EXISTS sla_warning_sent_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_complaints_sla_warning_sent_at ON complaints(sla_warning_sent_at);
