package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;

/**
 * Observability hook for audit processing.
 *
 * <p>The core listener depends on this small SPI instead of a concrete metrics library. Host
 * applications can provide their own implementation, while the auto-configuration module supplies a
 * Micrometer-backed implementation when Micrometer is present and a no-op implementation otherwise.
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
}
