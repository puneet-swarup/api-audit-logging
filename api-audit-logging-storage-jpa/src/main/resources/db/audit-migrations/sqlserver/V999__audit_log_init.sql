IF OBJECT_ID(N'dbo.api_audit_log', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.api_audit_log (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        service_name NVARCHAR(255) NULL,
        type NVARCHAR(20) NULL,
        method NVARCHAR(10) NULL,
        description NVARCHAR(255) NULL,
        url NVARCHAR(MAX) NULL,
        query_string NVARCHAR(MAX) NULL,
        request_headers NVARCHAR(MAX) NULL,
        response_headers NVARCHAR(MAX) NULL,
        request_body NVARCHAR(MAX) NULL,
        response_body NVARCHAR(MAX) NULL,
        http_status INT NULL,
        duration BIGINT NULL,
        correlation_id NVARCHAR(100) NULL,
        client_ip NVARCHAR(100) NULL,
        user_agent NVARCHAR(512) NULL,
        principal_name NVARCHAR(255) NULL,
        error_type NVARCHAR(255) NULL,
        error_message NVARCHAR(MAX) NULL,
        timestamp DATETIME2 NULL
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_audit_correlation' AND object_id = OBJECT_ID(N'dbo.api_audit_log'))
    CREATE INDEX idx_audit_correlation ON dbo.api_audit_log(correlation_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_audit_timestamp' AND object_id = OBJECT_ID(N'dbo.api_audit_log'))
    CREATE INDEX idx_audit_timestamp ON dbo.api_audit_log(timestamp);


