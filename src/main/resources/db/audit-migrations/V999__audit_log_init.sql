CREATE TABLE IF NOT EXISTS api_audit_log (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(255),
    type VARCHAR(20),
    method VARCHAR(10),
    description VARCHAR(100),
    url TEXT,
    request_body TEXT,
    response_body TEXT,
    http_status INT,
    duration BIGINT,
    correlation_id VARCHAR(50),
    timestamp TIMESTAMP WITHOUT TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_audit_correlation ON api_audit_log(correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON api_audit_log(timestamp);