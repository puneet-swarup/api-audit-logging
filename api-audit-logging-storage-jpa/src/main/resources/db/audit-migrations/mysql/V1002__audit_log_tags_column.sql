-- Adds the tags column used by path-based rules and custom policies.
-- Flyway runs this version exactly once (it is versioned), so no existence guard is needed.
ALTER TABLE api_audit_log ADD COLUMN tags LONGTEXT;
