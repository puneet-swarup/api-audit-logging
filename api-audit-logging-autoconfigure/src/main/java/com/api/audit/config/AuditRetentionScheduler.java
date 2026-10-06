package com.api.audit.config;

import com.api.audit.spi.AuditRetentionPolicy;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Storage-agnostic retention scheduler.
 *
 * <p>On the configured cron it invokes {@link AuditRetentionPolicy#purgeBefore(LocalDateTime)} on
 * every retention-capable store in the application context. This decouples retention from any
 * single storage backend: JPA, JDBC, memory, and custom stores all participate through the same
 * SPI.
 *
 * <p><b>Failure isolation.</b> A store that throws while purging is logged and skipped; other
 * stores still run. Retention must never destabilize the application.
 *
 * @author Puneet Swarup
 * @see AuditRetentionPolicy
 */
@Slf4j
public class AuditRetentionScheduler {

  private final List<AuditRetentionPolicy> policies;
  private final AuditLoggingProperties properties;

  /**
   * Creates the scheduler.
   *
   * @param policies the retention-capable stores; may be empty; never {@code null}
   * @param properties the library configuration properties
   */
  public AuditRetentionScheduler(
      List<AuditRetentionPolicy> policies, AuditLoggingProperties properties) {
    this.policies = policies == null ? List.of() : List.copyOf(policies);
    this.properties = properties;
  }

  /**
   * Purges expired records across every retention-capable store.
   *
   * <p>Schedule is controlled by {@code audit.logging.cleanup.cron} (default: daily at 2 AM).
   * Retention period is controlled by {@code audit.logging.cleanup.days} (default: 30).
   */
  @Scheduled(cron = "${audit.logging.cleanup.cron:0 0 2 * * *}")
  public void purgeExpired() {
    if (policies.isEmpty()) {
      log.debug("[AuditLog] Cleanup ran but no retention-capable store is registered.");
      return;
    }
    int days = properties.getCleanup().getDays();
    LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

    long total = 0;
    for (AuditRetentionPolicy policy : policies) {
      try {
        long deleted = policy.purgeBefore(cutoff);
        total += deleted;
        log.info(
            "[AuditLog] Retention: {} deleted {} record(s) older than {} days.",
            policy.getClass().getSimpleName(),
            deleted,
            days);
      } catch (RuntimeException ex) {
        log.error(
            "[AuditLog] Retention failed for {}: {}",
            policy.getClass().getSimpleName(),
            ex.getMessage(),
            ex);
      }
    }
    log.info("[AuditLog] Retention run complete — {} record(s) purged in total.", total);
  }
}
