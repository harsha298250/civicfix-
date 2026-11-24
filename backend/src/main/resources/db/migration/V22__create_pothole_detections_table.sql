-- V22: Add pothole detections table for computer vision localization and dimensional analysis
-- Forward-only migration preserving all existing complaint records and schemas

CREATE TABLE IF NOT EXISTS pothole_detections (
    id UUID PRIMARY KEY,
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    photo_id UUID REFERENCES complaint_photos(id) ON DELETE SET NULL,
    photo_url VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    pothole_count INTEGER NOT NULL DEFAULT 0,
    detections_json TEXT,
    severity VARCHAR(30) NOT NULL DEFAULT 'INCONCLUSIVE',
    severity_explanation TEXT,
    confidence NUMERIC(5,2),
    model_name VARCHAR(100),
    is_calibrated BOOLEAN NOT NULL DEFAULT FALSE,
    calibration_method VARCHAR(100),
    pixel_width INTEGER,
    pixel_height INTEGER,
    estimated_metric_width_cm NUMERIC(8,2),
    estimated_metric_length_cm NUMERIC(8,2),
    estimated_metric_area_sqm NUMERIC(8,4),
    measurement_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pothole_detections_complaint_id ON pothole_detections (complaint_id);
CREATE INDEX IF NOT EXISTS idx_pothole_detections_status ON pothole_detections (status);
