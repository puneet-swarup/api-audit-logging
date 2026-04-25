package com.api.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistent JPA entity representing a single API transaction record.
 *
 * <p>This entity acts as the schema definition for the {@code api_audit_logs} table. It captures
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
  private String description;

  /** The target URL or URI path of the transaction. */
  private String url;

  /**
   * The raw request payload.
   *
   * @implNote Mapped as {@code TEXT} to allow storage of large JSON or XML bodies that exceed the
   *     standard 255-character VARCHAR limit.
   */
  @Column(columnDefinition = "TEXT")
  private String requestBody;

  /**
   * The raw response payload.
   *
   * @implNote Mapped as {@code TEXT} to ensure complete capture of large response objects for
   *     debugging purposes.
   */
  @Column(columnDefinition = "TEXT")
  private String responseBody;

  /** The numeric HTTP status code returned by the server. */
  private int httpStatus;

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
  private String correlationId;

  /** The exact date and time the audit entry was recorded. */
  private LocalDateTime timestamp;
}
