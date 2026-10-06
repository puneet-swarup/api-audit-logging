package com.api.audit.policy;

import com.api.audit.model.AuditLogRecord;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether a captured record should actually be published to storage.
 *
 * <p>Sampling is the primary lever for controlling audit volume on high-traffic services. When a
 * sample rate below {@code 1.0} is configured, a fraction of <b>successful</b> records are stored
 * and the rest are dropped, while <b>error</b> records are always stored so that failures are never
 * lost to sampling.
 *
 * <p>The strategy is deliberately stateful only for cheap counters: a random decision is used per
 * record, and two {@link AtomicLong} counters track how many records were kept and dropped so
 * operators can verify the effective rate. The counters are exposed for tests and diagnostics.
 *
 * <p><b>Thread safety:</b> safe for concurrent use from the audit executor pool.
 *
 * @author Puneet Swarup
 * @see com.api.audit.config.AuditLoggingProperties.Sampling
 */
@Slf4j
public class SamplingStrategy {

  /** A record is treated as an error when its type ends with this suffix. */
  private static final String ERROR_SUFFIX = "_ERROR";

  private final boolean enabled;
  private final double sampleRate;
  private final boolean alwaysCaptureErrors;

  private final AtomicLong kept = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();

  /**
   * Creates the strategy from configuration.
   *
   * @param enabled whether sampling is active
   * @param sampleRate the fraction of non-error records to keep, in {@code [0.0, 1.0]}
   * @param alwaysCaptureErrors whether error records bypass sampling
   */
  public SamplingStrategy(boolean enabled, double sampleRate, boolean alwaysCaptureErrors) {
    this.enabled = enabled;
    // Clamp defensively; configuration is validated but the strategy is also constructed in tests.
    this.sampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
    this.alwaysCaptureErrors = alwaysCaptureErrors;

    if (enabled && this.sampleRate < 1.0) {
      log.info(
          "[AuditLog] Sampling enabled — sampleRate={}, alwaysCaptureErrors={}",
          this.sampleRate,
          alwaysCaptureErrors);
    }
  }

  /**
   * Returns whether the given record should be stored.
   *
   * @param record the captured record; never {@code null}
   * @return {@code true} to store the record, {@code false} to drop it
   */
  public boolean shouldStore(AuditLogRecord record) {
    if (!enabled || sampleRate >= 1.0) {
      kept.incrementAndGet();
      return true;
    }
    if (alwaysCaptureErrors && isError(record)) {
      kept.incrementAndGet();
      return true;
    }
    boolean keep = ThreadLocalRandom.current().nextDouble() < sampleRate;
    if (keep) {
      kept.incrementAndGet();
    } else {
      dropped.incrementAndGet();
    }
    return keep;
  }

  /**
   * @return the number of records the strategy chose to keep
   */
  public long getKeptCount() {
    return kept.get();
  }

  /**
   * @return the number of records the strategy chose to drop
   */
  public long getDroppedCount() {
    return dropped.get();
  }

  /**
   * @return whether sampling can ever drop a record (enabled and rate below 1.0)
   */
  public boolean isActive() {
    return enabled && sampleRate < 1.0;
  }

  private boolean isError(AuditLogRecord record) {
    String type = record.getType();
    if (type != null && type.endsWith(ERROR_SUFFIX)) {
      return true;
    }
    Integer status = record.getHttpStatus();
    return status != null && status >= 500;
  }
}
