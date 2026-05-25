package com.api.audit.storage.memory;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * In-memory implementation of audit storage.
 *
 * <p>This store is useful for local development, sample applications, and focused integration tests
 * where bringing up a database would add noise. It is intentionally simple: records live only for
 * the lifetime of the application process and are not meant for production retention.
 *
 * <p>The implementation is thread-safe for concurrent writes from the async audit executor.
 *
 * @author Puneet Swarup
 */
@Slf4j
public class InMemoryAuditLogStore implements AuditLogStore, AuditLogSearchStore {

  private final List<AuditLogRecord> records = new CopyOnWriteArrayList<>();

  /** Stores a record in process memory. */
  @Override
  public void save(AuditLogRecord record) {
    records.add(record);
  }

  /** Searches in-memory records using the same filters exposed by the internal audit endpoint. */
  @Override
  public Page<AuditLogRecord> search(
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
      Pageable pageable) {

    List<AuditLogRecord> filtered =
        records.stream()
            .filter(
                record -> correlationId == null || correlationId.equals(record.getCorrelationId()))
            .filter(record -> start == null || !record.getTimestamp().isBefore(start))
            .filter(record -> end == null || !record.getTimestamp().isAfter(end))
            .filter(record -> type == null || type.equals(record.getType()))
            .filter(
                record -> url == null || (record.getUrl() != null && record.getUrl().contains(url)))
            .filter(record -> serviceName == null || serviceName.equals(record.getServiceName()))
            .filter(record -> method == null || method.equals(record.getMethod()))
            .filter(record -> httpStatus == null || httpStatus.equals(record.getHttpStatus()))
            .filter(record -> clientIp == null || clientIp.equals(record.getClientIp()))
            .filter(
                record -> principalName == null || principalName.equals(record.getPrincipalName()))
            .filter(record -> errorType == null || errorType.equals(record.getErrorType()))
            .sorted(Comparator.comparing(AuditLogRecord::getTimestamp))
            .toList();

    int startIndex = (int) pageable.getOffset();
    if (startIndex >= filtered.size()) {
      return new PageImpl<>(List.of(), pageable, filtered.size());
    }

    int endIndex = Math.min(startIndex + pageable.getPageSize(), filtered.size());
    return new PageImpl<>(filtered.subList(startIndex, endIndex), pageable, filtered.size());
  }

  /** Clears all records. Intended for tests and demo reset flows. */
  public void clear() {
    records.clear();
  }
}
