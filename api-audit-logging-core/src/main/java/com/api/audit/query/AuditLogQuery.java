package com.api.audit.query;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Framework-neutral search criteria for querying stored audit records.
 *
 * <p>This object replaces the ad-hoc parameter list that previously carried a Spring Data {@code
 * Pageable} directly in the SPI. It has no framework dependencies, so a storage implementation — or
 * a custom {@code AuditLogSearchStore} supplied by an application — never needs to import Spring
 * Data merely to describe a query.
 *
 * <p>All fields are optional. A {@code null} field means "do not filter on this dimension".
 *
 * <p><b>Example</b>
 *
 * <pre>{@code
 * AuditLogQuery query = AuditLogQuery.builder()
 *     .type("INCOMING")
 *     .method("POST")
 *     .tagKey("module")
 *     .tagValue("payments")
 *     .page(0)
 *     .size(20)
 *     .sortBy("timestamp")
 *     .sortAscending(true)
 *     .build();
 * }</pre>
 *
 * @author Puneet Swarup
 * @see AuditLogPage
 */
@Value
@Builder
public class AuditLogQuery {

  /** Exact correlation ID to match; {@code null} = ignore. */
  String correlationId;

  /** Inclusive lower bound on timestamp; {@code null} = ignore. */
  LocalDateTime start;

  /** Inclusive upper bound on timestamp; {@code null} = ignore. */
  LocalDateTime end;

  /** Exact log type (e.g. {@code INCOMING}, {@code OUTGOING}); {@code null} = ignore. */
  String type;

  /** Substring match against the URL; {@code null} = ignore. */
  String url;

  /** Exact service name; {@code null} = ignore. */
  String serviceName;

  /** Exact HTTP method; {@code null} = ignore. */
  String method;

  /** Exact HTTP status; {@code null} = ignore. */
  Integer httpStatus;

  /** Exact client IP; {@code null} = ignore. */
  String clientIp;

  /** Exact principal name; {@code null} = ignore. */
  String principalName;

  /** Exact error type; {@code null} = ignore. */
  String errorType;

  /** Tag key to require; {@code null} = ignore. */
  String tagKey;

  /** Tag value to require when {@code tagKey} is set; {@code null} = any value for the key. */
  String tagValue;

  /** Zero-based page index. Negative values are treated as 0. */
  @Builder.Default int page = 0;

  /** Page size. Values below 1 are treated as a safe default of 20. */
  @Builder.Default int size = 20;

  /** Field to sort by. Defaults to {@code timestamp}. */
  @Builder.Default String sortBy = "timestamp";

  /** Whether the sort is ascending. Defaults to {@code true}. */
  @Builder.Default boolean sortAscending = true;

  /**
   * Returns the zero-based page index, clamped to a non-negative value.
   *
   * @return the effective page index
   */
  public int effectivePage() {
    return Math.max(page, 0);
  }

  /**
   * Returns the page size, clamped to a positive value.
   *
   * @return the effective page size
   */
  public int effectiveSize() {
    return size < 1 ? 20 : size;
  }

  /**
   * Returns the row offset derived from {@link #effectivePage()} and {@link #effectiveSize()}.
   *
   * @return the number of rows to skip
   */
  public long offset() {
    return (long) effectivePage() * effectiveSize();
  }
}
