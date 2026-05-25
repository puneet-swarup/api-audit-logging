package com.api.audit.webclient;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.util.AuditMetadataFormatter;
import java.time.LocalDateTime;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Auto-configuration for WebClient outbound audit logging.
 *
 * <p>Reactive request and response bodies are not consumed by this filter. Reading them here would
 * interfere with downstream subscribers unless each body is carefully re-published. Instead, this
 * first version captures reliable metadata: method, URL, status, timing, and correlation ID.
 *
 * @author Puneet Swarup
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

  /** Adds the audit exchange filter to WebClient builders managed by Spring Boot. */
  @Bean
  public WebClientCustomizer auditWebClientCustomizer(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    return builder -> builder.filter(auditExchangeFilter(publisher, properties));
  }

  private ExchangeFilterFunction auditExchangeFilter(
      ApplicationEventPublisher publisher, AuditLoggingProperties properties) {
    return (request, next) -> {
      String correlationId = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
      ClientRequest requestToUse =
          correlationId == null
              ? request
              : ClientRequest.from(request)
                  .header(CorrelationContext.CORRELATION_ID_HEADER, correlationId)
                  .build();

      long start = System.currentTimeMillis();
      return next.exchange(requestToUse)
          .doOnError(
              error ->
                  publisher.publishEvent(
                      new ApiLogEvent(
                          AuditLogRecord.builder()
                              .serviceName(appName)
                              .type("OUTGOING_TRANSPORT_ERROR")
                              .method(request.method().name())
                              .url(request.url().toString())
                              .queryString(request.url().getRawQuery())
                              .requestHeaders(
                                  AuditMetadataFormatter.headers(
                                      request.headers(),
                                      properties.getCapture().getMaxHeaderSize()))
                              .requestBody("[WEBCLIENT REQUEST BODY NOT CAPTURED]")
                              .responseBody("[WEBCLIENT RESPONSE BODY NOT CAPTURED]")
                              .duration(System.currentTimeMillis() - start)
                              .correlationId(correlationId)
                              .errorType(error.getClass().getName())
                              .errorMessage(error.getMessage())
                              .timestamp(LocalDateTime.now())
                              .build())))
          .flatMap(
              response -> {
                publisher.publishEvent(
                    new ApiLogEvent(
                        AuditLogRecord.builder()
                            .serviceName(appName)
                            .type("OUTGOING")
                            .method(request.method().name())
                            .url(request.url().toString())
                            .queryString(request.url().getRawQuery())
                            .requestHeaders(
                                AuditMetadataFormatter.headers(
                                    request.headers(), properties.getCapture().getMaxHeaderSize()))
                            .responseHeaders(
                                AuditMetadataFormatter.headers(
                                    response.headers().asHttpHeaders(),
                                    properties.getCapture().getMaxHeaderSize()))
                            .requestBody("[WEBCLIENT REQUEST BODY NOT CAPTURED]")
                            .responseBody("[WEBCLIENT RESPONSE BODY NOT CAPTURED]")
                            .httpStatus(response.statusCode().value())
                            .duration(System.currentTimeMillis() - start)
                            .correlationId(correlationId)
                            .timestamp(LocalDateTime.now())
                            .build()));
                return Mono.just(response);
              });
    };
  }
}
