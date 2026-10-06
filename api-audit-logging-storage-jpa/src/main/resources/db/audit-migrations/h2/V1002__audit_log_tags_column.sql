-- Adds the tags column used by path-based rules and custom policies.
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS tags CLOB;
