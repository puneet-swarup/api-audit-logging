package com.api.audit.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable value object representing a single captured API transaction.
 *
 * <p>This is the core data model of the audit library. It has zero framework-specific annotations —
 * no JPA, no Jackson, no persistence concerns. Every module that captures HTTP traffic (filters,
 * interceptors, Feign loggers, WebClient filters, RestTemplate interceptors) builds an {@code
 * AuditLogRecord} and publishes it via {@link com.api.audit.event.ApiLogEvent}.
 *
 * <p>Storage modules receive an {@code AuditLogRecord} and map it to their own persistence
 * representation (JPA entity, SQL row, in-memory object, etc.).
 *
 * <p>Example construction:
 *
 * <pre>{@code
 * AuditLogRecord record = AuditLogRecord.builder()
 *     .serviceName("payment-service")
 *     .type("INCOMING")
 *     .method("POST")
 *     .url("/api/v1/payments")
 *     .clientIp("10.10.10.15")
 *     .httpStatus(200)
 *     .correlationId("abc-123")
 *     .timestamp(LocalDateTime.now())
 *     .build();
 * }</pre>
 *
 * @author Puneet Swarup
 */
@Value
@Builder
public class AuditLogRecord {

  /** The name of the service that captured this record. From {@code spring.application.name}. */
  String serviceName;

  /**
   * The category of the transaction. Standard values: {@code INCOMING}, {@code OUTGOING}, {@code
   * OUTGOING_ERROR}, {@code OUTGOING_TRANSPORT_ERROR}.
   */
  String type;

  /** HTTP method: GET, POST, PUT, DELETE, PATCH, etc. */
  String method;

  /** Human-readable operation description. Sourced from the {@code @AuditLog} annotation value. */
  String description;

  /** The request URI path or full URL for outbound calls. */
  String url;

  /** Raw query string without the leading question mark. Useful for filtering and reproduction. */
  String queryString;

  /** Redacted request headers captured as a compact JSON object. */
  String requestHeaders;

  /** Redacted response headers captured as a compact JSON object. */
  String responseHeaders;

  /**
   * The request payload. Already masked for sensitive fields by {@link
   * com.api.audit.listener.ApiLogListener} before reaching any storage backend.
   */
  String requestBody;

  /**
   * The response payload. Already masked for sensitive fields. May contain sentinel values like
   * {@code "[STREAMING CONTENT NOT LOGGED]"}.
   */
  String responseBody;

  /** The HTTP response status code. Null when no HTTP response was received. */
  Integer httpStatus;

  /** Total round-trip duration in milliseconds. */
  long duration;

  /** Distributed tracing identifier. Propagated via the {@code X-Correlation-ID} header. */
  String correlationId;

  /** Best-effort client IP address for inbound requests. */
  String clientIp;

  /** User-Agent header for inbound requests when supplied by the caller. */
  String userAgent;

  /** Authenticated principal name when the hosting application exposes one. */
  String principalName;

  /** Exception class or transport error category when the audited call fails. */
  String errorType;

  /** Exception message or concise failure detail when the audited call fails. */
  String errorMessage;

  /** Exact timestamp when this record was captured. */
  LocalDateTime timestamp;
}
