package com.api.audit.controller;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.spi.AuditLogSearchStore;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
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
 * <p>This controller is the only place that knows about HTTP pagination. It accepts a Spring MVC
 * {@link Pageable} from the request, converts it into the framework-neutral {@link AuditLogQuery},
 * and returns the framework-neutral {@link AuditLogPage}. The storage SPI stays free of
 * web/framework concerns.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/internal/audit-logs")
@RequiredArgsConstructor
public class ApiLogController {

  private final AuditLogSearchStore searchStore;

  /**
   * Retrieves a paginated list of audit logs based on optional search criteria. All filter
   * parameters are optional. Omitted parameters are excluded from the query.
   *
   * @param correlationId exact correlation ID filter
   * @param start inclusive lower bound on timestamp
   * @param end inclusive upper bound on timestamp
   * @param type exact log type filter
   * @param url substring URL filter
   * @param serviceName exact service name filter
   * @param method exact HTTP method filter
   * @param httpStatus exact HTTP status filter
   * @param clientIp exact client IP filter
   * @param principalName exact principal name filter
   * @param errorType exact error type filter
   * @param tagKey required tag key
   * @param tagValue required tag value (used with {@code tagKey})
   * @param pageable HTTP pagination and sorting instructions
   * @return a page of matching audit records
   */
  @GetMapping
  public ResponseEntity<AuditLogPage<AuditLogRecord>> getLogs(
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
      @RequestParam(name = "tagKey", required = false) String tagKey,
      @RequestParam(name = "tagValue", required = false) String tagValue,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.ASC)
          Pageable pageable) {

    AuditLogQuery query =
        AuditLogQuery.builder()
            .correlationId(correlationId)
            .start(start)
            .end(end)
            .type(type)
            .url(url)
            .serviceName(serviceName)
            .method(method)
            .httpStatus(httpStatus)
            .clientIp(clientIp)
            .principalName(principalName)
            .errorType(errorType)
            .tagKey(tagKey)
            .tagValue(tagValue)
            .page(pageable.isPaged() ? pageable.getPageNumber() : 0)
            .size(pageable.isPaged() ? pageable.getPageSize() : 20)
            .sortBy(resolveSortField(pageable))
            .sortAscending(resolveSortAscending(pageable))
            .build();

    return ResponseEntity.ok(searchStore.search(query));
  }

  private String resolveSortField(Pageable pageable) {
    Sort sort = pageable.getSort();
    if (sort.isUnsorted()) {
      return "timestamp";
    }
    return sort.iterator().next().getProperty();
  }

  private boolean resolveSortAscending(Pageable pageable) {
    Sort sort = pageable.getSort();
    if (sort.isUnsorted()) {
      return true;
    }
    return sort.iterator().next().isAscending();
  }
}
