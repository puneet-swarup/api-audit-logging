DECLARE
  column_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'QUERY_STRING';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD query_string CLOB'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'REQUEST_HEADERS';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD request_headers CLOB'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'RESPONSE_HEADERS';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD response_headers CLOB'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'CLIENT_IP';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD client_ip VARCHAR2(100)'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'USER_AGENT';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD user_agent VARCHAR2(512)'; END IF;
  SELECT COUNT(*) INTO column_count FROM user_tab_columns WHERE table_name = 'API_AUDIT_LOG' AND column_name = 'PRINCIPAL_NAME';
  IF column_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE api_audit_log ADD principal_name VARCHAR2(255)'; END IF;
END;
