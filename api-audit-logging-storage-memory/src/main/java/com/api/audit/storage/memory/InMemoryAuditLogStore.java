package com.api.audit.storage.memory;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

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

  /** Searches in-memory records using the framework-neutral query contract. */
  @Override
  public AuditLogPage<AuditLogRecord> search(AuditLogQuery query) {
    List<AuditLogRecord> filtered =
        records.stream()
            .filter(
                r ->
                    query.getCorrelationId() == null
                        || query.getCorrelationId().equals(r.getCorrelationId()))
            .filter(r -> query.getStart() == null || !r.getTimestamp().isBefore(query.getStart()))
            .filter(r -> query.getEnd() == null || !r.getTimestamp().isAfter(query.getEnd()))
            .filter(r -> query.getType() == null || query.getType().equals(r.getType()))
            .filter(
                r ->
                    query.getUrl() == null
                        || (r.getUrl() != null && r.getUrl().contains(query.getUrl())))
            .filter(
                r ->
                    query.getServiceName() == null
                        || query.getServiceName().equals(r.getServiceName()))
            .filter(r -> query.getMethod() == null || query.getMethod().equals(r.getMethod()))
            .filter(
                r ->
                    query.getHttpStatus() == null
                        || query.getHttpStatus().equals(r.getHttpStatus()))
            .filter(r -> query.getClientIp() == null || query.getClientIp().equals(r.getClientIp()))
            .filter(
                r ->
                    query.getPrincipalName() == null
                        || query.getPrincipalName().equals(r.getPrincipalName()))
            .filter(
                r -> query.getErrorType() == null || query.getErrorType().equals(r.getErrorType()))
            .filter(r -> matchesTags(r, query.getTagKey(), query.getTagValue()))
            .sorted(timestampComparator(query.isSortAscending()))
            .toList();

    int size = query.effectiveSize();
    int startIndex = (int) query.offset();
    if (startIndex >= filtered.size()) {
      return new AuditLogPage<>(List.of(), query.effectivePage(), size, filtered.size());
    }
    int endIndex = Math.min(startIndex + size, filtered.size());
    return new AuditLogPage<>(
        filtered.subList(startIndex, endIndex), query.effectivePage(), size, filtered.size());
  }

  private Comparator<AuditLogRecord> timestampComparator(boolean ascending) {
    Comparator<AuditLogRecord> comparator = Comparator.comparing(AuditLogRecord::getTimestamp);
    return ascending ? comparator : comparator.reversed();
  }

  /**
   * Returns whether a record satisfies the tag filter. When no key is supplied every record
   * matches. When a key is supplied, the record must carry that key; when a value is also supplied,
   * the value must match exactly.
   *
   * @param record the candidate record
   * @param tagKey the required tag key; may be {@code null}
   * @param tagValue the required tag value; may be {@code null}
   * @return {@code true} when the record matches the tag filter
   */
  private boolean matchesTags(AuditLogRecord record, String tagKey, String tagValue) {
    if (tagKey == null || tagKey.isBlank()) {
      return true;
    }
    Map<String, String> tags = record.getTags();
    if (tags == null || !tags.containsKey(tagKey)) {
      return false;
    }
    return tagValue == null || tagValue.equals(tags.get(tagKey));
  }

  /** Clears all records. Intended for tests and demo reset flows. */
  public void clear() {
    records.clear();
  }
}
