-- ==============================================================================
-- CivicFix Database Migration: V21
-- Enterprise Transactional Email Outbox & Delivery State Machine
-- ==============================================================================

CREATE TABLE IF NOT EXISTS email_outbox (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    provider_message_id VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_retry_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_status_next_retry ON email_outbox (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_email_outbox_idempotency_key ON email_outbox (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_email_outbox_provider_msg_id ON email_outbox (provider_message_id);
CREATE INDEX IF NOT EXISTS idx_email_outbox_locked_at ON email_outbox (status, locked_at);
