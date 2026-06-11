package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;

/**
 * Default metrics implementation used when the host application has not provided one.
 *
 * <p>This keeps the library quiet in small applications while still allowing production services to
 * plug in Micrometer or a custom metrics bridge.
 *
 * @author Puneet Swarup
 */
public class NoOpAuditMetrics implements AuditMetrics {

  @Override
  public void recordSaved(AuditLogRecord record, long durationMillis) {
    // Intentionally empty.
  }

  @Override
  public void recordFailure(AuditLogRecord record, Exception exception) {
    // Intentionally empty.
  }
}
