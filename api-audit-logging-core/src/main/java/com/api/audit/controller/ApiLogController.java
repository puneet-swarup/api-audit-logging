package com.api.audit.controller;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogSearchStore;
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
 * Internal REST controller providing paginated access to persisted audit logs.
 *
 * <p>Protected by {@link com.api.audit.filter.AuditLogSecurityFilter} — every request must include
 * a valid {@code X-Audit-Api-Key} header. See {@code audit.logging.internal.api-key}.
 *
 * <p>Returns {@link AuditLogRecord} objects — the core model, storage-agnostic. The active {@link
 * AuditLogSearchStore} implementation handles the actual query.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/internal/audit-logs")
@RequiredArgsConstructor
public class ApiLogController {

  private final AuditLogSearchStore searchStore;

  /**
   * Retrieves a paginated list of audit logs based on optional search criteria. All parameters are
   * optional. Omitted parameters are excluded from the query.
   */
  @GetMapping
  public ResponseEntity<Page<AuditLogRecord>> getLogs(
      @RequestParam(name = "correlationId", required = false) String correlationId,
      @RequestParam(name = "start", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime start,
      @RequestParam(name = "end", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime end,
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "url", required = false) String url,
      @RequestParam(name = "serviceName", required = false) String serviceName,
      @RequestParam(name = "method", required = false) String method,
      @RequestParam(name = "httpStatus", required = false) Integer httpStatus,
      @RequestParam(name = "clientIp", required = false) String clientIp,
      @RequestParam(name = "principalName", required = false) String principalName,
      @RequestParam(name = "errorType", required = false) String errorType,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(
        searchStore.search(
            correlationId,
            start,
            end,
            type,
            url,
            serviceName,
            method,
            httpStatus,
            clientIp,
            principalName,
            errorType,
            pageable));
  }
}
