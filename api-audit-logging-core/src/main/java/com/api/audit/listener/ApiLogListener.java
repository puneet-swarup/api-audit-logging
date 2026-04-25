package com.api.audit.listener;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.util.JsonMasker;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous event listener responsible for the final processing and persistence of audit logs.
 *
 * <p>This component acts as the consumer in the library's event-driven architecture. It decouples
 * the primary request-handling threads from database I/O, ensuring that logging overhead does not
 * impact application throughput or user-perceived latency.
 *
 * <p>The listener also serves as a security checkpoint, applying data masking to sensitive
 * information within the payloads before they are written to the permanent store.
 *
 * @author Puneet Swarup
 * @see ApiLogEvent
 * @see ApiAuditLogRepository
 */
@Component
@AllArgsConstructor
public class ApiLogListener {

  private final ApiAuditLogRepository repository;
  private final JsonMasker jsonMasker;

  /**
   * Processes and persists the captured audit data.
   *
   * <p>This method is executed asynchronously using the {@code logExecutor} thread pool. Before
   * saving the entity to the database, it performs the following operations:
   *
   * <ol>
   *   <li>Extracts the {@link ApiAuditLog} from the published event.
   *   <li>Applies {@link JsonMasker} to the request body to redact sensitive PII/SPI data.
   *   <li>Applies {@link JsonMasker} to the response body for the same security constraints.
   *   <li>Commits the sanitized entity to the database via the JPA repository.
   * </ol>
   *
   * @param event the audit log event containing the raw transaction data
   * @implNote The {@link Async} annotation ensures this method returns immediately to the caller,
   *     typically the Spring ApplicationEventPublisher in the request thread.
   */
  @Async("logExecutor")
  @EventListener
  public void handleLog(ApiLogEvent event) {
    ApiAuditLog audit = event.log();

    // Security Enforcement: Mask sensitive fields before data hits the storage layer
    audit.setRequestBody(jsonMasker.mask(audit.getRequestBody()));
    audit.setResponseBody(jsonMasker.mask(audit.getResponseBody()));

    repository.save(audit);
  }
}
