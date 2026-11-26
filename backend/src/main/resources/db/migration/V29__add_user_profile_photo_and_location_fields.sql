-- V29: Add user profile photo storage reference and hierarchical location fields (district, zone)
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_object_key VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_url VARCHAR(1000);
ALTER TABLE users ADD COLUMN IF NOT EXISTS district VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS zone VARCHAR(150);
