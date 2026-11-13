ALTER TABLE dt_uploaded_file DROP CONSTRAINT dt_uploaded_file_parse_status_check;
ALTER TABLE dt_uploaded_file ADD CONSTRAINT dt_uploaded_file_parse_status_check
    CHECK (parse_status IN ('UPLOADED', 'PROCESSING', 'PARSED', 'FAILED', 'OCR_REQUIRED'));
ALTER TABLE dt_uploaded_file ADD COLUMN parse_error VARCHAR(64);
ALTER TABLE dt_uploaded_file ADD COLUMN import_title TEXT;
ALTER TABLE dt_uploaded_file ADD COLUMN import_domain VARCHAR(64);
ALTER TABLE dt_uploaded_file ADD COLUMN import_device_model VARCHAR(128);
ALTER TABLE dt_uploaded_file ADD COLUMN import_version VARCHAR(64);
ALTER TABLE dt_uploaded_file ADD COLUMN import_data_nature VARCHAR(16) NOT NULL DEFAULT 'SIMULATED'
    CHECK (import_data_nature IN ('SIMULATED', 'AUTHORIZED'));
