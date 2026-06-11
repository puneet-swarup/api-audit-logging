package com.api.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistent JPA entity representing a single API transaction record.
 *
 * <p>This entity acts as the schema definition for the {@code api_audit_log} table. It captures
 * high-fidelity data regarding HTTP exchanges, including headers, payloads, and performance
 * metrics, to facilitate system observability and post-mortem analysis.
 *
 * <p>The lifecycle of this entity is typically managed by the logging library's internal services
 * and is stored using a dedicated background thread pool to ensure no performance degradation of
 * the host application's request path.
 *
 * @author Puneet Swarup
 */
@Entity
@Table(name = "api_audit_log")
@Getter
@Setter
public class ApiAuditLog {

  /**
   * Unique primary key for the audit log entry. Uses an identity column strategy for database-side
   * ID generation.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The name of the microservice or application that generated this log. Value is typically derived
   * from the {@code spring.application.name} property.
   */
  private String serviceName;

  /**
   * The category of the logged event.
   *
   * <p>Standard values include:
   *
   * <ul>
   *   <li>{@code INCOMING}: Request received by the service.
   *   <li>{@code OUTGOING}: Request sent by the service (e.g., via Feign).
   *   <li>{@code ERROR}: Failure scenarios during processing.
   * </ul>
   */
  private String type;

  /** The HTTP method associated with the transaction (e.g., GET, POST, PUT). */
  private String method;

  /** A human-readable description or title of the captured event. */
  @Column(length = 255)
  private String description;

  /** The target URL or URI path of the transaction. */
  @Lob private String url;

  /** Raw query string captured without the leading question mark. */
  @Lob private String queryString;

  /** Redacted request headers captured as JSON. */
  @Lob private String requestHeaders;

  /** Redacted response headers captured as JSON. */
  @Lob private String responseHeaders;

  /**
   * The raw request payload.
   *
   * <p><b>Implementation note:</b> Mapped as a JPA {@link Lob} so each database dialect can choose
   * the right large-text type.
   */
  @Lob private String requestBody;

  /**
   * The raw response payload.
   *
   * <p><b>Implementation note:</b> Mapped as a JPA {@link Lob} so each database dialect can choose
   * the right large-text type.
   */
  @Lob private String responseBody;

  /** The numeric HTTP status code returned by the server. Null for transport failures. */
  private Integer httpStatus;

  /**
   * Total processing time in milliseconds. Measured from the moment the request is intercepted
   * until the response is finalized.
   */
  private long duration;

  /**
   * A unique string used to link related requests across distributed components.
   *
   * <p>This value is essential for tracing a request as it hops between multiple services. It is
   * propagated via the {@code X-Correlation-ID} header.
   */
  @Column(length = 100)
  private String correlationId;

  /** Best-effort client IP address for inbound requests. */
  @Column(length = 100)
  private String clientIp;

  /** User-Agent header for inbound requests when supplied by the caller. */
  @Column(length = 512)
  private String userAgent;

  /** Authenticated principal name when available from the hosting application. */
  @Column(length = 255)
  private String principalName;

  /** Exception class or transport error category when the audited call fails. */
  @Column(length = 255)
  private String errorType;

  /** Exception message or concise failure detail when the audited call fails. */
  @Lob private String errorMessage;

  /** The exact date and time the audit entry was recorded. */
  private LocalDateTime timestamp;
}
