package com.api.audit.config;

import com.api.audit.spi.AuditMetrics;
import com.api.audit.spi.NoOpAuditMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for audit metrics.
 *
 * <p>A no-op implementation is registered when the host application has not provided its own
 * metrics bridge and no optional metrics auto-configuration has supplied one.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(before = LoggingAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditMetricsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AuditMetrics.class)
  public AuditMetrics auditMetrics() {
    return new NoOpAuditMetrics();
  }
}
