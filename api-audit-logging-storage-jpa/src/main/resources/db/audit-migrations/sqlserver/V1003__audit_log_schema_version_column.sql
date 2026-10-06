-- Adds the schema_version column for downstream event contract evolution.
IF COL_LENGTH('api_audit_log', 'schema_version') IS NULL
BEGIN
  ALTER TABLE api_audit_log ADD schema_version INT;
END
