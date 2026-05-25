ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS query_string CLOB;
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS request_headers CLOB;
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS response_headers CLOB;
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS client_ip VARCHAR(100);
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);
ALTER TABLE api_audit_log ADD COLUMN IF NOT EXISTS principal_name VARCHAR(255);
