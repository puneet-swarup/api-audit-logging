package com.api.audit.feign;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.util.JsonMasker;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Feign outbound audit logging.
 *
 * <p>This module owns everything that is specific to OpenFeign: the custom logger, the optional
 * error decoder, and correlation ID propagation for outbound requests. Keeping this configuration
 * outside the common auto-config module lets applications use the core audit library without
 * pulling Feign into services that do not need it.
 *
 * <p>When this module is on the classpath and audit logging is enabled, Feign calls annotated with
 * {@code @AuditLog} are captured as outgoing audit events and flow through the same masking,
 * asynchronous listener, and storage pipeline as inbound HTTP requests.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(afterName = "com.api.audit.config.LoggingAutoConfiguration")
@ConditionalOnClass(feign.Logger.class)
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnProperty(
    prefix = "audit.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FeignAuditAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  /**
   * Provides the logger that captures Feign responses and publishes outbound audit events.
   *
   * <p>The logger re-buffers response bodies after reading them, so downstream Feign decoding can
   * continue normally.
   */
  @Bean
  public feign.Logger feignLogger(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    return new CustomFeignLogger(publisher, appName, properties);
  }

  /**
   * Sets Feign logging to {@code FULL}.
   *
   * <p>Feign only exposes enough request and response detail to the logger at this level. The
   * custom logger still avoids console logging; it uses Feign's logger hook only as the capture
   * point for audit events.
   */
  @Bean
  public feign.Logger.Level feignLevel() {
    return feign.Logger.Level.FULL;
  }

  /**
   * Propagates the current correlation ID to outbound Feign requests.
   *
   * <p>The inbound servlet filter stores the correlation ID in MDC. This interceptor carries that
   * same value into the next service call through the standard {@code X-Correlation-ID} header.
   */
  @Bean
  public RequestInterceptor correlationInterceptor() {
    return template -> {
      String id = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
      if (id != null) template.header(CorrelationContext.CORRELATION_ID_HEADER, id);
    };
  }

  /**
   * Configures an audit-aware Feign {@link ErrorDecoder}.
   *
   * <p>This bean is opt-in because some teams already have carefully tuned error decoders. When
   * enabled, it wraps the existing decoder instead of replacing its behavior, so business
   * exceptions still come from the application's own decoder after the failed response is audited.
   *
   * <p>Activation: set {@code audit.logging.feign-error.enabled=true}.
   */
  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "audit.logging.feign-error",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public ErrorDecoder errorDecoder(
      ApplicationEventPublisher publisher,
      List<ErrorDecoder> existingDecoders,
      JsonMasker jsonMasker,
      AuditLoggingProperties properties) {

    ErrorDecoder serviceDecoder =
        existingDecoders.stream()
            .filter(decoder -> !(decoder instanceof CustomFeignErrorDecoder))
            .findFirst()
            .orElse(new ErrorDecoder.Default());

    return new CustomFeignErrorDecoder(publisher, appName, serviceDecoder, jsonMasker, properties);
  }
}
