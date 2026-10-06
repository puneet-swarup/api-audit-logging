-- Adds the schema_version column for downstream event contract evolution.
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS schema_version INT;
