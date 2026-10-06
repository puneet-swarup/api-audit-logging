package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;

/**
 * Observability hook for audit processing.
 *
 * <p>The core listener depends on this small SPI instead of a concrete metrics library. Host
 * applications can provide their own implementation, while the auto-configuration module supplies a
 * Micrometer-backed implementation when Micrometer is present and a no-op implementation otherwise.
 *
 * <p>Implementations must never throw; metrics failures must not affect audit capture or request
 * handling.
 *
 * @author Puneet Swarup
 */
public interface AuditMetrics {

  /**
   * Called after an audit record is successfully handed to the active store.
   *
   * @param record the record that was persisted or published
   * @param durationMillis time spent in the storage call
   */
  void recordSaved(AuditLogRecord record, long durationMillis);

  /**
   * Called when masking or storage fails while processing an audit record.
   *
   * @param record the original record when available
   * @param exception the failure that prevented the record from being stored
   */
  void recordFailure(AuditLogRecord record, Exception exception);

  /**
   * Called when a captured record is intentionally not stored.
   *
   * <p>This covers the two deliberate-drop paths: the executor rejecting a task because the queue
   * is full, and sampling deciding to skip a non-error record. Recording these separately from
   * failures lets operators distinguish "we chose not to store this" from "we tried and could not".
   *
   * @param reason a short, low-cardinality reason code such as {@code QUEUE_FULL} or {@code
   *     SAMPLED}
   * @param record the record that was dropped; may be {@code null}
   */
  void recordDropped(String reason, AuditLogRecord record);
}
