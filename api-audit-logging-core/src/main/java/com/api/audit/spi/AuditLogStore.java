package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;

/**
 * Service Provider Interface for audit log persistence.
 *
 * <p>This is the central extension point of the audit library. Implement this interface to provide
 * a custom storage backend.
 *
 * <p>Built-in implementations (choose via dependency):
 *
 * <ul>
 *   <li>{@code api-audit-logging-storage-jpa} - persists via Spring Data JPA (default starter)
 *   <li>{@code api-audit-logging-storage-jdbc} - persists via JdbcTemplate (no ORM)
 *   <li>{@code api-audit-logging-storage-memory} - stores in-memory (testing/dev)
 *   <li>{@code api-audit-logging-storage-kafka} - publishes audit records to Kafka
 * </ul>
 *
 * <p>Custom implementation example (e.g. Elasticsearch):
 *
 * <pre>{@code
 * @Component
 * public class ElasticsearchAuditLogStore implements AuditLogStore {
 *
 *     @Override
 *     public void save(AuditLogRecord record) {
 *         elasticsearchClient.index(record);
 *     }
 * }
 * }</pre>
 *
 * <p>The autoconfiguration uses {@code @ConditionalOnMissingBean(AuditLogStore.class)}, so a custom
 * implementation takes precedence automatically with no extra configuration.
 *
 * @author Puneet Swarup
 */
public interface AuditLogStore {

  /**
   * Persists a single captured audit record.
   *
   * <p>Called asynchronously from the {@code logExecutor} thread pool after masking.
   * Implementations must be thread-safe and must never throw an exception that would propagate to
   * the calling request thread.
   *
   * @param record the fully assembled, already-masked audit record; never {@code null}
   */
  void save(AuditLogRecord record);
}
