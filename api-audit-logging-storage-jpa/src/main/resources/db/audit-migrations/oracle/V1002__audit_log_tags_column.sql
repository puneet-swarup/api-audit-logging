-- Adds the tags column used by path-based rules and custom policies.
DECLARE
  column_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'TAGS';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD tags CLOB'; END IF;
END;
