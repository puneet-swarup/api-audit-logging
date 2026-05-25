# Kafka and SIEM Consumer Guide

This guide explains how teams can use the Kafka storage module when audit records need to leave the
application and flow into a central data platform, SIEM, warehouse, or search system.

Kafka storage is useful when audit logs should be consumed by another platform instead of being
queried directly from the application database.

## Typical Flow

```text
Spring Boot application
  -> api-audit-logging-storage-kafka
  -> Kafka topic: api-audit-logs
  -> Kafka Connect / stream processor / SIEM collector
  -> OpenSearch, Splunk, Elasticsearch, data lake, warehouse, or alerting system
```

The application is responsible for producing audit records. Downstream systems are responsible for
retention, indexing, alerting, dashboards, and long-term reporting.

## Producer Configuration

Add the Kafka storage module:

```gradle
dependencies {
    implementation "com.api.audit:api-audit-logging-autoconfigure:2.0.0"
    implementation "com.api.audit:api-audit-logging-storage-kafka:2.0.0"
}
```

Enable Kafka as the active audit sink:

```yaml
audit:
  logging:
    storage:
      type: kafka
    kafka:
      enabled: true
      topic: api-audit-logs

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

For production, prefer a topic name that reflects the owning platform or environment:

```yaml
audit:
  logging:
    kafka:
      topic: platform.audit.api-events.v1
```

## Event Shape

The Kafka message contains the shared `AuditLogRecord` model. A typical event looks like this:

```json
{
  "serviceName": "order-service",
  "type": "OUTGOING",
  "method": "POST",
  "description": "CREATE_PAYMENT",
  "url": "https://payment-service/payments",
  "queryString": null,
  "requestHeaders": "{\"Authorization\":[\"******\"]}",
  "responseHeaders": "{\"Content-Type\":[\"application/json\"]}",
  "requestBody": "{\"amount\":1250}",
  "responseBody": "{\"message\":\"gateway timeout\"}",
  "httpStatus": 504,
  "duration": 312,
  "correlationId": "corr-7f9f4d0b",
  "clientIp": null,
  "userAgent": null,
  "principalName": "puneet",
  "errorType": "HTTP_504",
  "errorMessage": "Gateway Timeout",
  "timestamp": "2026-05-25T04:42:00.000"
}
```

Sensitive headers and configured sensitive JSON fields are masked before the record reaches Kafka.

## Recommended Topic Design

Start simple unless your organization already has a platform standard.

| Setting | Recommendation |
|---|---|
| Topic name | `platform.audit.api-events.v1` or `api-audit-logs` |
| Key | `correlationId` when available |
| Partitions | Size for expected traffic and consumer parallelism |
| Retention | Match compliance and investigation needs |
| Schema | Treat `AuditLogRecord` as the first version of the event contract |

Using `correlationId` as the Kafka key keeps records for the same logical transaction more likely
to land in the same partition. That is helpful for ordered downstream processing.

## Example Consumer

A small Spring Kafka consumer can read and forward audit records to another system:

```java
@Component
class AuditEventConsumer {

    private final AuditSearchIndexer indexer;

    AuditEventConsumer(AuditSearchIndexer indexer) {
        this.indexer = indexer;
    }

    @KafkaListener(topics = "platform.audit.api-events.v1", groupId = "audit-indexer")
    void consume(AuditLogRecord record) {
        indexer.index(record);
    }
}
```

The `AuditSearchIndexer` could write to OpenSearch, Elasticsearch, Splunk HEC, a warehouse loader,
or a company-owned audit service.

## Example Alert Rules

Once records are in a SIEM or search platform, teams can create useful operational and security
signals.

| Signal | Example rule |
|---|---|
| Downstream instability | Alert when `type=OUTGOING_ERROR` or `httpStatus >= 500` is high for one service |
| Unauthorized access | Alert when `httpStatus=401` or `httpStatus=403` spikes |
| Slow dependency | Alert when `duration > 3000` for outbound calls to a critical service |
| Sensitive endpoint pressure | Watch high traffic to authentication, payment, or user data APIs |
| Failed user journey | Search all records with the same `correlationId` after a customer complaint |

Example query idea:

```text
serviceName = "order-service"
AND type = "OUTGOING"
AND httpStatus >= 500
AND timestamp >= now() - 5m
```

## Security Notes

- Keep masking rules up to date for domain-specific sensitive fields.
- Keep Kafka topics private. Audit logs can contain URLs, identifiers, and business metadata.
- Prefer TLS and authenticated Kafka clients in production.
- Align Kafka retention with legal, privacy, and compliance expectations.
- Avoid storing raw secrets in request bodies. Masking helps, but applications should still avoid
  sending secrets where possible.

## When to Pair Kafka With a Search Store

Kafka is intentionally a write sink. It does not power `/internal/audit-logs` by itself.

Use Kafka alone when a central platform owns querying and retention. Pair Kafka with JPA, JDBC, or a
custom `AuditLogSearchStore` when the application should also expose local audit search.

