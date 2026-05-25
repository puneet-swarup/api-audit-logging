package com.api.audit.listener;

import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import com.api.audit.util.JsonMasker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous event listener that bridges the capture layer and the storage layer.
 *
 * <p>This component sits between the HTTP capture components and the storage backend. It receives
 * {@link ApiLogEvent} from the Spring event bus, applies data masking, and delegates persistence to
 * the active {@link AuditLogStore} implementation.
 *
 * <p><b>Why masking happens here and not in the store:</b> Masking in the listener means every
 * storage backend — JPA, JDBC, in-memory, or a custom Kafka sink — automatically receives masked
 * data without needing to implement masking themselves.
 *
 * <p><b>Why exceptions are swallowed:</b> Audit failure must never propagate to the request thread.
 * The application must continue serving requests even if logging fails.
 *
 * @author Puneet Swarup
 * @see AuditLogStore
 * @see JsonMasker
 */
@Slf4j
@Component
@AllArgsConstructor
public class ApiLogListener {

  private final AuditLogStore auditLogStore;
  private final JsonMasker jsonMasker;

  /**
   * Processes and persists the captured audit data asynchronously.
   *
   * <p>Executed on the {@code logExecutor} thread pool, ensuring this method returns immediately to
   * the caller and database I/O never blocks the request thread.
   *
   * @param event the audit log event containing the raw captured transaction data
   */
  @Async("logExecutor")
  @EventListener
  public void handleLog(ApiLogEvent event) {
    AuditLogRecord original = event.record();

    // Build a new immutable record with masked bodies.
    // AuditLogRecord is immutable (@Value) so we rebuild using the builder.
    AuditLogRecord masked =
        AuditLogRecord.builder()
            .serviceName(original.getServiceName())
            .type(original.getType())
            .method(original.getMethod())
            .description(original.getDescription())
            .url(original.getUrl())
            .queryString(original.getQueryString())
            .requestHeaders(original.getRequestHeaders())
            .responseHeaders(original.getResponseHeaders())
            .requestBody(jsonMasker.mask(original.getRequestBody()))
            .responseBody(jsonMasker.mask(original.getResponseBody()))
            .httpStatus(original.getHttpStatus())
            .duration(original.getDuration())
            .correlationId(original.getCorrelationId())
            .clientIp(original.getClientIp())
            .userAgent(original.getUserAgent())
            .principalName(original.getPrincipalName())
            .errorType(original.getErrorType())
            .errorMessage(original.getErrorMessage())
            .timestamp(original.getTimestamp())
            .build();

    try {
      auditLogStore.save(masked);
    } catch (Exception e) {
      // Audit failure must NEVER propagate to the calling request thread.
      log.error(
          "[AuditLog] Failed to persist audit record for correlationId={}: {}",
          original.getCorrelationId(),
          e.getMessage(),
          e);
    }
  }
}
