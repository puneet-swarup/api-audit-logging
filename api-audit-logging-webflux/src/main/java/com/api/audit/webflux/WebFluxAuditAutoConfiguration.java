package com.api.audit.webflux;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.policy.AuditDecisionEngine;
import com.api.audit.policy.SamplingStrategy;
import com.api.audit.spi.AuditMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

/**
 * Auto-configuration for reactive (WebFlux) inbound audit capture.
 *
 * <p>Registers the {@link ReactiveIncomingAuditFilter} when a reactive web application is present
 * and audit logging is enabled. It reuses the same decision engine, policies, sampling, and metrics
 * as the servlet path, so path-based rules work identically in reactive apps.
 *
 * @author Puneet Swarup
 * @see ReactiveIncomingAuditFilter
 */
@AutoConfiguration(afterName = "com.api.audit.config.LoggingAutoConfiguration")
@ConditionalOnClass(WebFilter.class)
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebFluxAuditAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  /**
   * Registers the reactive inbound audit filter.
   *
   * @param publisher publishes audit events
   * @param properties the library configuration properties
   * @param decisionEngine resolves whether a request is audited
   * @param samplingStrategy decides whether a captured record is stored
   * @param auditMetrics reports dropped records
   * @return the reactive filter
   */
  @Bean
  @ConditionalOnMissingBean(ReactiveIncomingAuditFilter.class)
  public ReactiveIncomingAuditFilter reactiveIncomingAuditFilter(
      ApplicationEventPublisher publisher,
      AuditLoggingProperties properties,
      AuditDecisionEngine decisionEngine,
      SamplingStrategy samplingStrategy,
      AuditMetrics auditMetrics) {
    return new ReactiveIncomingAuditFilter(
        publisher, appName, properties, decisionEngine, samplingStrategy, auditMetrics);
  }
}
