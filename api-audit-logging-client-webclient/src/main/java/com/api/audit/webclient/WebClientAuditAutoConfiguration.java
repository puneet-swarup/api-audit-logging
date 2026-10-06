package com.api.audit.webclient;

import com.api.audit.config.AuditLoggingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for WebClient outbound audit logging.
 *
 * <p>Registers a {@link WebClientCustomizer} that adds the audit {@link
 * org.springframework.web.reactive.function.client.ExchangeFilterFunction} to every Spring-managed
 * {@code WebClient.Builder}. Metadata is always captured; response-body capture is opt-in via
 * {@code audit.logging.webclient.capture-bodies=true}.
 *
 * @author Puneet Swarup
 * @see WebClientAuditFilter
 */
@AutoConfiguration(afterName = "com.api.audit.config.LoggingAutoConfiguration")
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebClientAuditAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  /**
   * Adds the audit exchange filter to WebClient builders managed by Spring Boot.
   *
   * @param publisher publishes audit events
   * @param properties the library configuration properties
   * @return the WebClient customizer
   */
  @Bean
  public WebClientCustomizer auditWebClientCustomizer(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    WebClientAuditFilter auditFilter = new WebClientAuditFilter(publisher, appName, properties);
    return builder -> builder.filter(auditFilter.filter());
  }
}
