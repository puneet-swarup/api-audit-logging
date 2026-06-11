package com.api.audit.config;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Micrometer-backed audit metrics bridge.
 *
 * <p>The implementation intentionally keeps tag cardinality low. Record type is safe because the
 * library emits a small known set such as {@code INCOMING}, {@code OUTGOING}, and error variants.
 * URLs, correlation IDs, headers, and descriptions are deliberately not used as metric tags.
 *
 * @author Puneet Swarup
 */
public class MicrometerAuditMetrics implements AuditMetrics {

  private final MeterRegistry meterRegistry;

  public MicrometerAuditMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void recordSaved(AuditLogRecord record, long durationMillis) {
    String type = metricType(record);
    Counter.builder("api.audit.records.saved")
        .description("Audit records successfully saved or published")
        .tag("type", type)
        .register(meterRegistry)
        .increment();
    Timer.builder("api.audit.store.duration")
        .description("Time spent handing an audit record to the active store")
        .tag("type", type)
        .register(meterRegistry)
        .record(Duration.ofMillis(durationMillis));
  }

  @Override
  public void recordFailure(AuditLogRecord record, Exception exception) {
    Counter.builder("api.audit.records.failed")
        .description("Audit records that failed during masking or storage")
        .tag("type", metricType(record))
        .tag("exception", exception == null ? "unknown" : exception.getClass().getSimpleName())
        .register(meterRegistry)
        .increment();
  }

  private String metricType(AuditLogRecord record) {
    return record == null || record.getType() == null ? "UNKNOWN" : record.getType();
  }
}
