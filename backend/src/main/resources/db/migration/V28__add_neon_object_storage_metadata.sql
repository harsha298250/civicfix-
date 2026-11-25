-- CivicFix AI — V28 Add Neon S3-Compatible Object Storage Metadata
-- Adds storage provider tracking, private S3 object keys, uploader attribution,
-- and SHA-256 integrity checksums to complaint_photos and resolution_evidence.

-- 1. Enhance complaint_photos for private S3 storage
ALTER TABLE complaint_photos ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE complaint_photos ADD COLUMN IF NOT EXISTS object_key VARCHAR(500);
ALTER TABLE complaint_photos ADD COLUMN IF NOT EXISTS uploader_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE complaint_photos ADD COLUMN IF NOT EXISTS sha256_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_complaint_photos_object_key ON complaint_photos(object_key);
CREATE INDEX IF NOT EXISTS idx_complaint_photos_uploader ON complaint_photos(uploader_id);

-- 2. Enhance resolution_evidence for private S3 storage
ALTER TABLE resolution_evidence ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE resolution_evidence ADD COLUMN IF NOT EXISTS object_key VARCHAR(500);
ALTER TABLE resolution_evidence ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE resolution_evidence ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
ALTER TABLE resolution_evidence ADD COLUMN IF NOT EXISTS sha256_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_resolution_evidence_object_key ON resolution_evidence(object_key);
