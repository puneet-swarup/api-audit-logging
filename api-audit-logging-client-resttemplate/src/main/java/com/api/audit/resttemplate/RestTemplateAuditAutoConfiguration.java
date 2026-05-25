package com.api.audit.resttemplate;

import com.api.audit.config.AuditLoggingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for Spring's blocking HTTP clients.
 *
 * <p>Any {@link RestTemplate} built through Spring Boot's builder/customizer pipeline receives the
 * audit interceptor automatically when this module is present. The same interceptor is also applied
 * to Spring Framework 6's {@link RestClient}, which is the modern blocking HTTP client available in
 * Spring Boot 3.x applications.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(afterName = "com.api.audit.config.LoggingAutoConfiguration")
@ConditionalOnClass(RestTemplate.class)
@org.springframework.boot.context.properties.EnableConfigurationProperties(
    AuditLoggingProperties.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RestTemplateAuditAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  /** Adds the audit interceptor to RestTemplate instances managed by Spring Boot. */
  @Bean
  public RestTemplateCustomizer auditRestTemplateCustomizer(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    return restTemplate ->
        restTemplate
            .getInterceptors()
            .add(new AuditRestTemplateInterceptor(publisher, appName, properties));
  }

  /** Adds the audit interceptor to RestClient builders managed by Spring Boot. */
  @Bean
  @ConditionalOnClass(RestClient.class)
  public RestClientCustomizer auditRestClientCustomizer(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    return builder ->
        builder.requestInterceptor(
            new AuditRestTemplateInterceptor(publisher, appName, properties));
  }
}
