IF COL_LENGTH('dbo.api_audit_log', 'error_type') IS NULL
    ALTER TABLE dbo.api_audit_log ADD error_type NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.api_audit_log', 'error_message') IS NULL
    ALTER TABLE dbo.api_audit_log ADD error_message NVARCHAR(MAX) NULL;
