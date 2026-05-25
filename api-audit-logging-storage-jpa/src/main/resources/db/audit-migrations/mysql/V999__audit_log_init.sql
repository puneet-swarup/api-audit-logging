CREATE TABLE IF NOT EXISTS api_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_name VARCHAR(255),
    type VARCHAR(20),
    method VARCHAR(10),
    description VARCHAR(255),
    url LONGTEXT,
    query_string LONGTEXT,
    request_headers LONGTEXT,
    response_headers LONGTEXT,
    request_body LONGTEXT,
    response_body LONGTEXT,
    http_status INT,
    duration BIGINT,
    correlation_id VARCHAR(100),
    client_ip VARCHAR(100),
    user_agent VARCHAR(512),
    principal_name VARCHAR(255),
    error_type VARCHAR(255),
    error_message LONGTEXT,
    timestamp DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_audit_correlation (correlation_id),
    INDEX idx_audit_timestamp (timestamp)
);


