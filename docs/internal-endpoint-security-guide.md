# Internal Endpoint Security Guide

The `/internal/audit-logs` endpoint can expose sensitive operational data. Treat it as an
administrative endpoint, not as a normal application API.

The library includes a built-in fail-secure API key filter, but host applications should still place
the endpoint behind their normal production controls such as network restrictions, Spring Security,
gateway policies, and audit/admin roles.

## Built-In Protection

When a searchable store is active, the starter exposes:

```http
GET /internal/audit-logs
X-Audit-Api-Key: your-secret
```

The built-in filter checks every request whose path starts with `/internal/audit-logs`.

| Situation | Result |
|---|---:|
| `audit.logging.internal.api-key` is missing | `403 Forbidden` |
| `audit.logging.internal.api-key` is blank | `403 Forbidden` |
| `X-Audit-Api-Key` is missing | `401 Unauthorized` |
| `X-Audit-Api-Key` is wrong | `401 Unauthorized` |
| `X-Audit-Api-Key` matches | Request continues |

This means the endpoint is not accidentally open when an application forgets to configure the key.

## Required Configuration

Do not hard-code the key in source-controlled YAML. Load it from a secret manager or environment
variable:

```yaml
audit:
  logging:
    internal:
      api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}
```

For local demos only:

```yaml
audit:
  logging:
    internal:
      api-key: dev-only-key
```

## What the Host Application Should Still Do

The API key is a library-level guard. In production, it should not be the only boundary.

Recommended host controls:

- Restrict `/internal/audit-logs` at the gateway, ingress, load balancer, or firewall.
- Allow access only from trusted admin networks, VPN, or internal tooling.
- Add Spring Security rules so only audit/admin users or service accounts can reach it.
- Use TLS everywhere; the API key is a secret.
- Rotate the key periodically and after staff or platform changes.
- Keep audit-log access itself observable in gateway or application logs.
- Avoid exposing the endpoint on public internet routes.

Example Spring Security rule:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/internal/audit-logs/**").hasRole("AUDIT_ADMIN")
            .anyRequest().authenticated())
        .build();
}
```

In this setup, both checks apply:

```text
Spring Security role check
  + X-Audit-Api-Key check
  = defense in depth
```

## Deployment Guidance

For production, prefer one of these patterns:

| Pattern | Notes |
|---|---|
| Private admin route | Expose `/internal/audit-logs` only on internal ingress or VPN |
| Central audit service | Disable local search and stream to Kafka/SIEM instead |
| Admin UI backend | Let an authenticated admin backend call the endpoint, never browsers directly |
| Custom `AuditLogSearchStore` | Keep data in a central controlled search service |

Kafka-only deployments do not expose `/internal/audit-logs` from this library because Kafka is a
write sink and does not implement searchable storage.

## Data Sensitivity

The library masks common sensitive fields and headers before storage, but audit records may still
contain:

- URLs and query strings
- user or account identifiers
- client IP addresses
- user agents
- principal names
- error messages
- request/response metadata

That data can be useful for operations and compliance, but it can also be sensitive. Protect the
search endpoint with the same care as logs, traces, and admin dashboards.

## Disabling the Endpoint

The endpoint is only created when an `AuditLogSearchStore` bean exists. If an application does not
need local search, use a write-only sink such as Kafka or provide no searchable store.

You can also disable the entire audit auto-configuration:

```yaml
audit:
  logging:
    enabled: false
```
