# API Audit Logging — Examples & Cookbook

This module is the reference catalogue for the library. Every option, feature, and integration has a named, runnable example here. Each example lives in its own class (single responsibility) so you can open exactly one file to understand one thing, then copy the pattern into your service.

> Goal: a maintainer or integrator should be able to find the configuration for any feature, run it, and observe its effect on stored audit records — without reading the library internals.

## How to run

    .\gradlew.bat :api-audit-logging-examples:bootRun --args="--spring.profiles.active=pathbased"

Replace `pathbased` with any profile from the table below.

Every profile exposes the internal search endpoint so you can inspect captured records:

    GET http://localhost:8080/internal/audit-logs
    Header: X-Audit-Api-Key: examples-key

## Profiles to features

| Profile | Feature demonstrated | Config file |
|---|---|---|
| `pathbased` | Every path-rule option: Ant, regex, methods, capture modes, tags, skip | `application-pathbased.yaml` |
| `masking` | Built-in + configured field masking and a custom `PayloadMasker` | `application-masking.yaml` |
| `policy` | A custom `AuditPolicy` bean driven by a request header | `application-policy.yaml` |
| `observability` | Sampling (store a fraction of successes, always keep errors) | `application-observability.yaml` |
| `memory` | In-memory storage instead of a database | `application-memory.yaml` |

## Feature map (every option)

### Capturing requests

| Feature | Where | How to try |
|---|---|---|
| Annotation on a class | `annotation.AnnotationCaptureExample` | `GET /examples/annotation/hello` |
| Annotation on a method | `annotation.AnnotationCaptureExample` | `POST /examples/annotation/echo` |
| Path-based, Ant pattern | `pathbased.PathBasedRulesExample` | `GET /examples/pathbased/hello` |
| Path-based, regex matcher | `pathbased.PathBasedRulesExample` | `GET /examples/pathbased/orders/42` |
| Path-based, method scoping | `pathbased.PathBasedRulesExample` | `POST /examples/pathbased/mutations` |
| Path-based, explicit skip | `pathbased.PathBasedRulesExample` | `GET /examples/pathbased/skip` |
| Custom policy (SPI) | `policy.HeaderBasedAuditPolicy` | `GET /examples/pathbased/skip` with header `X-Audit: on` |

### Capture modes

| Mode | Effect | Where |
|---|---|---|
| `FULL` (default) | Headers and bodies stored | `GET /examples/pathbased/hello` |
| `METADATA_ONLY` | Headers stored, bodies null | `GET /examples/pathbased/metadata` |
| `BODY_ONLY` | Bodies stored, headers null | `POST /examples/pathbased/body-only` |

### Masking

| Feature | Where | How to try |
|---|---|---|
| Built-in sensitive fields | default masker | `POST /examples/masking/payment` with `password`/`cvv` |
| Additional configured fields | `masking.additional-fields` | same, with `otp` |
| Nested objects/arrays | default masker | same, with nested `card.cvv` |
| Custom masker (SPI) | `masking.CustomPayloadMasker` | `masking` profile scrubs emails too |

### Outbound clients

| Client | Endpoint | Integration module |
|---|---|---|
| Feign | `GET /examples/clients/feign/42` | `api-audit-logging-client-feign` |
| RestTemplate | `GET /examples/clients/resttemplate/42` | `api-audit-logging-client-resttemplate` |
| RestClient | `GET /examples/clients/restclient/42` | `api-audit-logging-client-resttemplate` |
| WebClient | `GET /examples/clients/webclient/42` | `api-audit-logging-client-webclient` |

Correlation ID is propagated, so inbound and outbound records share the same value.

### Storage

| Sink | Profile | Notes |
|---|---|---|
| JPA | default | The starter default; Flyway manages the schema |
| JDBC | add `storage.type: jdbc` | No JPA; same table |
| Memory | `memory` | No database; records lost on shutdown |
| Kafka | add Kafka module + config | Streaming sink; see the Kafka guide |

### Observability

| Feature | Config | Effect |
|---|---|---|
| Sampling | `audit.logging.sampling.*` | `observability` profile stores ~1 in 4 successes; errors always kept |
| Metrics | Micrometer on classpath | `api.audit.records.saved/failed/dropped`, `api.audit.store.duration` |

## The internal search endpoint

Filter captured records:

    GET /internal/audit-logs?type=INCOMING&method=GET
    GET /internal/audit-logs?tagKey=module&tagValue=examples

Supported filters: `correlationId`, `start`, `end`, `type`, `url`, `serviceName`, `method`, `httpStatus`, `clientIp`, `principalName`, `errorType`, `tagKey`, `tagValue`.

## Integrating into production

1. Add the starter.

       implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"

2. Pick a storage and, optionally, path-based auditing and sampling.

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
                   tags: { module: payments }
           sampling:
             enabled: true
             sample-rate: 0.1

3. Restart. No controller changes are required for path-based rules.

See the repository README and `docs/` for the full reference and guides.

## Tests

Every example is covered by an integration test that asserts the documented effect on stored records, so this module is proven, not just described:

- `PathBasedRulesExampleTest` — each path-rule option.
- `MaskingExampleTest` — built-in, configured, and custom masking.
- `HeaderBasedAuditPolicyTest` — custom policy discovery and tagging.
