# API Audit Logging — Demo App

This module is a runnable sample that shows how to integrate and use every feature of the audit
logging library. It doubles as living documentation: each profile, endpoint, and integration path
below maps to a real capability you can copy into your own service.

## Run it

    .\gradlew.bat :api-audit-demo-app:bootRun --args="--spring.profiles.active=jpa"

Replace `jpa` with any profile below.

## Profiles

| Profile | Storage | Search endpoint | What it demonstrates |
|---|---|---|---|
| `jpa` | JPA (default) | yes | Default starter setup with database storage |
| `jdbc` | JDBC | yes | Plain JdbcTemplate storage without JPA |
| `memory` | In-memory | yes | Tests and local experiments; records are lost on shutdown |
| `kafka` | Kafka | no | Streaming audit records to a broker |
| `path-controls` | JPA | yes | Ant-style include/exclude path controls |
| `path-based` | JPA | yes | Zero-code-change path auditing (no annotations) |

Set `KAFKA_BOOTSTRAP_SERVERS` when using the `kafka` profile.

## Endpoints by feature

### Annotation-based capture (existing)

| Endpoint | What it demonstrates |
|---|---|
| `GET /api/v1/hello?name=X` | Method-level `@AuditLog` capture |
| `GET /api/v2/hello?name=X` | Class-level `@AuditLog` capture |
| `POST /api/v1/echo` | Request/response body capture and masking |

### Path-based capture (no annotations) — `path-based` profile only

The `PathAuditDemoController` has **no `@AuditLog` annotation**. Capture is driven entirely by
configuration in `application-path-based.yaml`.

| Endpoint | Rule feature demonstrated |
|---|---|
| `GET /api/v1/path-audited/hello` | Broad Ant rule with description, tags, FULL capture |
| `POST /api/v1/path-audited/echo` | FULL capture of request and response bodies |
| `GET /api/v1/path-audited/metadata/ping` | `capture: METADATA_ONLY` (no bodies stored) |
| `POST /api/v1/path-audited/body-only/echo` | `capture: BODY_ONLY` (no headers stored) |
| `POST /api/v1/path-audited/mutations` | Method-scoped rule (`methods: [POST, PUT, PATCH]`) |
| `GET /api/v1/path-audited/orders/42` | Regex matcher rule (`matcher: REGEX`) |
| `GET /actuator/health` | Explicit skip rule (`audit: false`) |

### Outbound client capture

| Endpoint | What it demonstrates |
|---|---|
| `GET /api/v1/multi-hop` | Inbound plus outbound RestTemplate correlation |
| `GET /api/v1/demo/feign` | Feign outbound capture |
| `GET /api/v1/demo/webclient` | WebClient outbound metadata capture |
| `GET /api/v1/demo/restclient` | RestClient outbound capture |
| `GET /api/v1/demo/http-interface` | HTTP interface client backed by an audited RestClient |
| `GET /api/v1/demo/no-audit/ping` | Path exclusion when the `path-controls` profile is active |

Set `DEMO_DOWNSTREAM_BASE_URL` or `DEMO_DOWNSTREAM_STATUS_URL` to point outbound examples at a real
local service.

## Querying captured records

The `jpa`, `jdbc`, `memory`, and `path-based` profiles expose the internal search endpoint:

    GET /internal/audit-logs?correlationId=...&type=INCOMING
    Header: X-Audit-Api-Key: dev-only-key

Supported filters: `correlationId`, `start`, `end`, `type`, `url`, `serviceName`, `method`,
`httpStatus`, `clientIp`, `principalName`, `errorType`. Pagination via `page`, `size`, `sort`.

## Integrating into your own service

1. Add the dependency.

       implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"

2. Choose your storage and, optionally, path-based auditing.

       audit:
         logging:
           storage:
             type: jpa
           policies:
             path-based:
               enabled: true
               rules:
                 - pattern: /api/v1/payments/**
                   description: Payment APIs
                   tags:
                     module: payments

3. Restart. Annotated endpoints and path-based rules both work; no code changes are required for
   the path-based rules.

See the repository README and `docs/path-based-auditing-guide.md` for the complete reference.
