-- Adds the tags column used by path-based rules and custom policies.
IF COL_LENGTH('api_audit_log', 'tags') IS NULL
BEGIN
  ALTER TABLE api_audit_log ADD tags NVARCHAR(MAX);
END
