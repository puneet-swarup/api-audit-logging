package com.api.audit.config;

import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for automated maintenance and data retention of audit logs.
 *
 * <p>Activation is controlled via {@code audit.logging.cleanup.enabled=true}. The retention period
 * and schedule are configurable via {@link AuditLoggingProperties.Cleanup}.
 *
 * @author Puneet Swarup
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "audit.logging.cleanup.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CleanupConfig {

  private final ApiAuditLogRepository repository;
  private final AuditLoggingProperties properties;

  /**
   * Performs a background purge of historical audit records.
   *
   * <p>Schedule is controlled by {@code audit.logging.cleanup.cron} (default: daily at 2 AM).
   * Retention period is controlled by {@code audit.logging.cleanup.days} (default: 30).
   *
   * @implNote Ensure the {@code timestamp} column is indexed for performance on large tables.
   * @see ApiAuditLogRepository#deleteByTimestampBefore(LocalDateTime)
   */
  @Scheduled(cron = "${audit.logging.cleanup.cron:0 0 2 * * *}")
  public void purgeOldLogs() {
    int days = properties.getCleanup().getDays();
    LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
    repository.deleteByTimestampBefore(cutoff);
    log.info("[AuditLog] Cleanup complete — deleted records older than {} days.", days);
  }
}
