DECLARE
  column_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'ERROR_TYPE';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD error_type VARCHAR2(255)'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'ERROR_MESSAGE';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD error_message CLOB'; END IF;
END;
