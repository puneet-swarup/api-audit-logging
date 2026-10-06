-- Adds the schema_version column for downstream event contract evolution.
-- Flyway runs this version exactly once, so no existence guard is needed.
ALTER TABLE api_audit_log ADD COLUMN schema_version INT;
