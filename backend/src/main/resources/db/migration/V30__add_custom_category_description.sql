ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS custom_category_description VARCHAR(200);
