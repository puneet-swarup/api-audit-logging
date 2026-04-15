package com.api.audit.service;

import com.api.audit.controller.ApiLogController;
import com.api.audit.entity.ApiAuditLog;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.repository.ApiLogSpecifications;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service layer responsible for the retrieval and advanced filtering of API audit logs.
 *
 * <p>This service acts as a facade between the {@link ApiLogController} and the {@link
 * ApiAuditLogRepository}. It leverages dynamic specifications to provide a robust search interface
 * for the auditing dashboard.
 *
 * <p>Operational characteristics for 2026:
 *
 * <ul>
 *   <li>Supports large-scale datasets through Spring Data pagination.
 *   <li>Encapsulates complex criteria building logic within the repository layer.
 * </ul>
 *
 * @author Puneet Swarup
 */
@Service
@RequiredArgsConstructor
public class ApiLogSearchService {

  private final ApiAuditLogRepository repository;

  /**
   * Executes a paginated search for audit logs based on dynamic criteria.
   *
   * <p>This method combines multiple optional parameters into a single database query. If all
   * filter parameters are {@code null}, the method returns all logs within the requested {@link
   * Pageable} range.
   *
   * @param start the earliest timestamp to include in the result set (inclusive)
   * @param end the latest timestamp to include in the result set (inclusive)
   * @param type the transaction category (e.g., INCOMING, OUTGOING, ERROR)
   * @param url a string snippet to perform a partial match against request URLs
   * @param correlationId the specific trace ID used to isolate a single logical transaction flow
   * @param pageable pagination parameters including page number, size, and sorting instructions
   * @return a {@link Page} of {@link ApiAuditLog} entities matching the specified criteria
   * @see ApiLogSpecifications#withFilters(LocalDateTime, LocalDateTime, String, String, String)
   */
  public Page<ApiAuditLog> search(
      LocalDateTime start,
      LocalDateTime end,
      String type,
      String url,
      String correlationId,
      Pageable pageable) {

    return repository.findAll(
        ApiLogSpecifications.withFilters(start, end, type, url, correlationId), pageable);
  }
}
