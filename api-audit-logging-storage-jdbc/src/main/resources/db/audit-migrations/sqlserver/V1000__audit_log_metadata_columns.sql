IF COL_LENGTH('dbo.api_audit_log', 'query_string') IS NULL
    ALTER TABLE dbo.api_audit_log ADD query_string NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'request_headers') IS NULL
    ALTER TABLE dbo.api_audit_log ADD request_headers NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'response_headers') IS NULL
    ALTER TABLE dbo.api_audit_log ADD response_headers NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'client_ip') IS NULL
    ALTER TABLE dbo.api_audit_log ADD client_ip NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'user_agent') IS NULL
    ALTER TABLE dbo.api_audit_log ADD user_agent NVARCHAR(512) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'principal_name') IS NULL
    ALTER TABLE dbo.api_audit_log ADD principal_name NVARCHAR(255) NULL;
