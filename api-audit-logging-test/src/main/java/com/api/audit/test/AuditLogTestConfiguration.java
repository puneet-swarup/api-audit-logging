package com.api.audit.test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link CapturedAuditLogs} bean for tests that use {@link EnableAuditLoggingTest}.
 *
 * <p>A user-supplied {@code CapturedAuditLogs} bean takes precedence, so a test can provide its own
 * if it needs different behavior.
 *
 * @author Puneet Swarup
 */
@Configuration(proxyBeanMethods = false)
public class AuditLogTestConfiguration {

  /**
   * Registers the capture bean.
   *
   * @return the captured-audit-logs collector
   */
  @Bean
  @ConditionalOnMissingBean
  public CapturedAuditLogs capturedAuditLogs() {
    return new CapturedAuditLogs();
  }
}
