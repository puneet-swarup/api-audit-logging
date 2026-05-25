If you discover a security vulnerability, please open a private issue. We take security seriously and will address PII masking concerns as a priority.

The `/internal/audit-logs` endpoint is protected by a fail-secure API key filter. Missing or blank
`audit.logging.internal.api-key` values block the endpoint. Production applications should also use
host-level controls such as Spring Security, gateway restrictions, private networks, TLS, and secret
rotation.

See [Internal Endpoint Security Guide](docs/internal-endpoint-security-guide.md).
