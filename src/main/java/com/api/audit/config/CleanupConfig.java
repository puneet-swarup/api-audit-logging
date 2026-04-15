package com.api.audit.config;

import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for automated maintenance and data retention of audit logs.
 *
 * <p>High-volume logging systems require strict lifecycle management to optimize storage costs and
 * maintain query performance. This class provides an automated purge mechanism to remove stale
 * audit data based on a configurable retention period.
 *
 * <p>Activation is controlled via the property {@code audit.logging.cleanup.enabled}.
 *
 * @author Puneet Swarup
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "audit.logging.cleanup.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CleanupConfig {

  private final ApiAuditLogRepository repository;

  /**
   * The number of days to retain logs before they are eligible for deletion.
   *
   * <p>Defaults to 30 days if the property {@code audit.logging.cleanup.days} is not explicitly
   * defined in the environment configuration.
   */
  @Value("${audit.logging.cleanup.days:30}")
  private int daysToKeep;

  /**
   * Performs a background purge of historical audit records.
   *
   * <p>This task is scheduled to execute daily at 02:00 AM server time to minimize impact on
   * peak-hour database performance. It calculates a cutoff point relative to the current execution
   * time and invokes a bulk deletion of all records older than that timestamp.
   *
   * @implNote The purge uses a derived delete query. For 2026 scale environments processing
   *     millions of logs daily, ensure the {@code timestamp} column is indexed to prevent table
   *     scans during the cleanup process.
   * @see ApiAuditLogRepository#deleteByTimestampBefore(LocalDateTime)
   */
  @Scheduled(cron = "0 0 2 * * *") // Runs every night at 2 AM
  public void purgeOldLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
    repository.deleteByTimestampBefore(cutoff);
  }
}
