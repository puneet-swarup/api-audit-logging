-- Adds the tags column used by path-based rules and custom policies.
-- MySQL lacks ADD COLUMN IF NOT EXISTS on all supported versions, so guard via information_schema.
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_audit_log' AND COLUMN_NAME = 'tags'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE api_audit_log ADD COLUMN tags LONGTEXT',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
