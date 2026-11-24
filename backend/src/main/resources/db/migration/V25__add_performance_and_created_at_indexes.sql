-- V25: Add performance and composite sorting indexes for production complaint pagination
-- Accelerates citizen history, worker queue, officer review, and administrative analytics

CREATE INDEX IF NOT EXISTS idx_complaints_created_at_desc
    ON complaints (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_citizen_created_at
    ON complaints (citizen_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_status_created_at
    ON complaints (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_worker_created_at
    ON complaints (assigned_worker_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_officer_created_at
    ON complaints (assigned_officer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_category_created_at
    ON complaints (category, created_at DESC);
