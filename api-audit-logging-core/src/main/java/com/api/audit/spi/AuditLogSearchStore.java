package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service Provider Interface for querying persisted audit logs.
 *
 * <p>Implementations translate the search parameters into their backend-specific query mechanism
 * (JPA Specification, SQL WHERE clause, in-memory filter, etc.).
 *
 * <p>The JPA implementation is provided by {@code api-audit-logging-storage-jpa}.
 *
 * @author Puneet Swarup
 */
public interface AuditLogSearchStore {

  /**
   * Executes a paginated search based on the provided criteria. All parameters are optional — null
   * values are excluded from the query.
   *
   * @param correlationId filter by exact correlation ID match; null = ignore
   * @param start filter for records at or after this timestamp; null = ignore
   * @param end filter for records at or before this timestamp; null = ignore
   * @param type filter by log type (INCOMING, OUTGOING, OUTGOING_ERROR, OUTGOING_TRANSPORT_ERROR);
   *     null = ignore
   * @param url filter by URL containing this string; null = ignore
   * @param serviceName filter by exact service name; null = ignore
   * @param method filter by exact HTTP method; null = ignore
   * @param httpStatus filter by exact HTTP status; null = ignore
   * @param clientIp filter by exact client IP; null = ignore
   * @param principalName filter by exact authenticated principal name; null = ignore
   * @param errorType filter by exact error type; null = ignore
   * @param pageable pagination and sorting instructions; never {@code null}
   * @return a page of matching records; empty page if none match
   */
  Page<AuditLogRecord> search(
      String correlationId,
      LocalDateTime start,
      LocalDateTime end,
      String type,
      String url,
      String serviceName,
      String method,
      Integer httpStatus,
      String clientIp,
      String principalName,
      String errorType,
      Pageable pageable);
}
