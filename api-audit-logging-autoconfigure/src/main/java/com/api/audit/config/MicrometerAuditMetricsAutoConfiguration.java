package com.api.audit.config;

import com.api.audit.spi.AuditMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Optional Micrometer metrics auto-configuration for audit logging.
 *
 * <p>This class is only active when Micrometer is on the host application's classpath and a {@link
 * MeterRegistry} bean is available.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(before = AuditMetricsAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MicrometerAuditMetricsAutoConfiguration {

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean(AuditMetrics.class)
  public AuditMetrics micrometerAuditMetrics(MeterRegistry meterRegistry) {
    return new MicrometerAuditMetrics(meterRegistry);
  }
}
