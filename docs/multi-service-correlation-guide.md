# Multi-Service Correlation Guide

This guide shows how to use `X-Correlation-ID` to connect audit records across multiple services.
It is one of the most useful parts of the library when debugging distributed systems.

## Why Correlation Matters

One user action often touches several APIs:

```text
mobile app
  -> api-gateway
  -> order-service
  -> payment-service
  -> inventory-service
```

Without a shared correlation ID, each service has its own isolated logs. With a shared correlation
ID, the same user journey can be searched as one story.

## Example Journey

Assume the first incoming request has:

```http
X-Correlation-ID: corr-checkout-20260525-001
```

The audit records across services may look like this:

| Service | Type | Method | URL | Status |
|---|---|---:|---|---:|
| api-gateway | INCOMING | POST | `/checkout` | 200 |
| order-service | INCOMING | POST | `/orders` | 201 |
| order-service | OUTGOING | POST | `https://payment-service/payments` | 504 |
| payment-service | INCOMING_ERROR | POST | `/payments` | 504 |
| order-service | OUTGOING | POST | `https://inventory-service/reservations` | 200 |

Every row should carry:

```text
correlationId = corr-checkout-20260525-001
```

That gives support, engineering, and audit teams a direct way to reconstruct what happened.

## How the Library Propagates the ID

For inbound Spring MVC requests, the library reads `X-Correlation-ID` and stores it in request
context. If the caller did not send one, the library creates a new value.

For outbound clients, the library propagates the current correlation ID:

| Client | Propagation path |
|---|---|
| Feign | Request interceptor |
| RestTemplate | Client HTTP request interceptor |
| RestClient | Client HTTP request interceptor |
| HTTP interface clients | Covered when backed by an audited `RestClient.Builder` |
| WebClient | Exchange filter function |

The result is that downstream services receive the same `X-Correlation-ID` header.

## Querying One Trace Locally

When using JPA, JDBC, or memory storage, query the internal endpoint:

```http
GET /internal/audit-logs?correlationId=corr-checkout-20260525-001
X-Audit-Api-Key: dev-only-key
```

You can combine filters when investigating a specific failure:

```http
GET /internal/audit-logs?correlationId=corr-checkout-20260525-001&type=OUTGOING&httpStatus=504
X-Audit-Api-Key: dev-only-key
```

Useful filters include `serviceName`, `method`, `httpStatus`, `clientIp`, `principalName`, and
`errorType`.

## Querying One Trace From Kafka or SIEM

When records are published to Kafka and indexed centrally, search by `correlationId` across all
services.

Example query idea:

```text
correlationId = "corr-checkout-20260525-001"
ORDER BY timestamp ASC
```

For failure analysis:

```text
correlationId = "corr-checkout-20260525-001"
AND (httpStatus >= 500 OR errorType IS NOT NULL)
ORDER BY timestamp ASC
```

## Recommended Service Setup

Each participating service should:

- Include the starter or the needed client/storage modules.
- Preserve incoming `X-Correlation-ID` values from trusted upstream services.
- Let the library propagate the ID through Feign, RestTemplate, RestClient, HTTP interface clients,
  or WebClient.
- Use the same Kafka topic or central index when the organization wants cross-service visibility.
- Include `serviceName` through `spring.application.name` so the trace tells which service produced
  each record.

Example configuration:

```yaml
spring:
  application:
    name: order-service

audit:
  logging:
    enabled: true
    storage:
      type: kafka
    kafka:
      enabled: true
      topic: platform.audit.api-events.v1
```

## Practical Debugging Example

A customer reports: "Checkout failed at 10:15 AM."

The support or engineering team searches by the correlation ID from the API response header or
gateway logs:

```text
correlationId = "corr-checkout-20260525-001"
```

They find:

```text
10:15:01 api-gateway     INCOMING       POST /checkout                       200
10:15:01 order-service   INCOMING       POST /orders                         201
10:15:02 order-service   OUTGOING       POST /payments                       504
10:15:02 payment-service INCOMING_ERROR POST /payments                       504
```

Now the failure is no longer vague. The checkout path reached order creation, but payment failed
with a gateway timeout.

## Operational Tips

- Return the correlation ID in API responses if your gateway or application standard allows it.
- Ask support teams to include correlation IDs in tickets.
- Keep clocks synchronized across services so timestamp ordering is useful.
- Use the same header name across all services: `X-Correlation-ID`.
- Avoid regenerating the ID in downstream services unless the incoming value is missing or invalid.

