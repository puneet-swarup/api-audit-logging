-- Adds the schema_version column for downstream event contract evolution.
DECLARE
  column_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'SCHEMA_VERSION';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD schema_version NUMBER(10)'; END IF;
END;
