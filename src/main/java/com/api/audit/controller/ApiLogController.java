package com.api.audit.controller;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.service.ApiLogSearchService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative REST controller providing access to persisted API audit logs.
 *
 * <p>This controller exposes endpoints for querying and filtering audit data captured by the
 * library. It is mapped to the {@code /internal} namespace, suggesting it should be restricted via
 * security configurations in a production environment.
 *
 * @author Puneet Swarup
 * @see ApiAuditLog
 * @see ApiLogSearchService
 */
@RestController
@RequestMapping("/internal/audit-logs")
@RequiredArgsConstructor
public class ApiLogController {
  private final ApiLogSearchService searchService;

  /**
   * Retrieves a paginated list of audit logs based on various search criteria.
   *
   * <p>All parameters are optional. If no filters are provided, the method returns the most recent
   * logs based on the default pagination settings.
   *
   * @param correlationId unique identifier to track a specific request flow
   * @param start the beginning of the time range (ISO 8601 format)
   * @param end the end of the time range (ISO 8601 format)
   * @param type the nature of the log (e.g., INBOUND, OUTBOUND)
   * @param url the endpoint URL or pattern to filter by
   * @param pageable pagination and sorting metadata. Defaults to 20 records per page, sorted by
   *     {@code timestamp} ASC.
   * @return a {@link ResponseEntity} containing a {@link Page} of {@link ApiAuditLog} entries
   */
  @GetMapping
  public ResponseEntity<Page<ApiAuditLog>> getLogs(
      @RequestParam(required = false) String correlationId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime end,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String url,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(searchService.search(start, end, type, url, correlationId, pageable));
  }
}
