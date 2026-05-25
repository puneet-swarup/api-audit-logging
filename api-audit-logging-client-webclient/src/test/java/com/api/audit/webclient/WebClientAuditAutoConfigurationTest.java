package com.api.audit.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Unit tests for WebClient audit auto-configuration.
 *
 * <p>The test keeps the HTTP exchange in memory so it can validate the auto-configured filter
 * without depending on a running web server. This is enough to prove that a Spring-managed builder
 * receives the audit filter and publishes the expected audit event when an outbound call completes.
 *
 * @author Puneet Swarup
 */
class WebClientAuditAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebClientAuditAutoConfiguration.class))
          .withPropertyValues("spring.application.name=webclient-test-service")
          .withUserConfiguration(TestPublisherConfig.class);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void registersWebClientCustomizer() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(WebClientCustomizer.class));
  }

  @Test
  void doesNotRegisterWebClientCustomizerWhenAuditLoggingDisabled() {
    contextRunner
        .withPropertyValues("audit.logging.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(WebClientCustomizer.class));
  }

  @Test
  void publishesAuditEventWhenWebClientCallCompletes() {
    contextRunner.run(
        context -> {
          CapturingAuditListener listener = context.getBean(CapturingAuditListener.class);
          WebClientCustomizer customizer = context.getBean(WebClientCustomizer.class);
          WebClient.Builder builder =
              WebClient.builder()
                  .exchangeFunction(
                      request ->
                          Mono.just(
                              ClientResponse.create(HttpStatus.ACCEPTED)
                                  .header("X-Result", "accepted")
                                  .build()));

          MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-webclient-1");
          customizer.customize(builder);

          builder
              .build()
              .get()
              .uri("https://example.org/orders/42?source=mobile")
              .header("Authorization", "Bearer secret")
              .retrieve()
              .toBodilessEntity()
              .block();

          assertThat(listener.apiLogEvents()).hasSize(1);
          ApiLogEvent event = listener.apiLogEvents().getFirst();

          assertThat(event.record().getServiceName()).isEqualTo("webclient-test-service");
          assertThat(event.record().getType()).isEqualTo("OUTGOING");
          assertThat(event.record().getMethod()).isEqualTo("GET");
          assertThat(event.record().getUrl())
              .isEqualTo("https://example.org/orders/42?source=mobile");
          assertThat(event.record().getQueryString()).isEqualTo("source=mobile");
          assertThat(event.record().getRequestHeaders()).contains("\"Authorization\":[\"******\"]");
          assertThat(event.record().getRequestHeaders()).doesNotContain("Bearer secret");
          assertThat(event.record().getResponseHeaders()).contains("X-Result");
          assertThat(event.record().getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
          assertThat(event.record().getCorrelationId()).isEqualTo("corr-webclient-1");
        });
  }

  @Test
  void publishesAuditEventForErrorResponseBeforeRetrieveThrows() {
    contextRunner.run(
        context -> {
          CapturingAuditListener listener = context.getBean(CapturingAuditListener.class);
          WebClientCustomizer customizer = context.getBean(WebClientCustomizer.class);
          WebClient.Builder builder =
              WebClient.builder()
                  .exchangeFunction(
                      request ->
                          Mono.just(
                              ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                  .header("Retry-After", "30")
                                  .build()));

          MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-webclient-error-1");
          customizer.customize(builder);

          assertThatThrownBy(
                  () ->
                      builder
                          .build()
                          .get()
                          .uri("https://example.org/unavailable")
                          .retrieve()
                          .toBodilessEntity()
                          .block())
              .isInstanceOf(
                  org.springframework.web.reactive.function.client.WebClientResponseException
                      .class);

          assertThat(listener.apiLogEvents()).hasSize(1);
          ApiLogEvent event = listener.apiLogEvents().getFirst();
          assertThat(event.record().getHttpStatus())
              .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
          assertThat(event.record().getResponseHeaders()).contains("Retry-After");
          assertThat(event.record().getCorrelationId()).isEqualTo("corr-webclient-error-1");
        });
  }

  @Test
  void publishesAuditEventForTransportFailure() {
    contextRunner.run(
        context -> {
          CapturingAuditListener listener = context.getBean(CapturingAuditListener.class);
          WebClientCustomizer customizer = context.getBean(WebClientCustomizer.class);
          WebClient.Builder builder =
              WebClient.builder()
                  .exchangeFunction(
                      request -> Mono.error(new IllegalStateException("connection refused")));

          customizer.customize(builder);

          assertThatThrownBy(
                  () ->
                      builder
                          .build()
                          .get()
                          .uri("https://example.org/down")
                          .retrieve()
                          .toBodilessEntity()
                          .block())
              .isInstanceOf(IllegalStateException.class);

          assertThat(listener.apiLogEvents()).hasSize(1);
          ApiLogEvent event = listener.apiLogEvents().getFirst();
          assertThat(event.record().getType()).isEqualTo("OUTGOING_TRANSPORT_ERROR");
          assertThat(event.record().getHttpStatus()).isNull();
          assertThat(event.record().getErrorType())
              .isEqualTo(IllegalStateException.class.getName());
          assertThat(event.record().getErrorMessage()).contains("connection refused");
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class TestPublisherConfig {

    @Bean
    CapturingAuditListener capturingAuditListener() {
      return new CapturingAuditListener();
    }
  }

  static class CapturingAuditListener {

    private final List<ApiLogEvent> apiLogEvents = new ArrayList<>();

    @EventListener
    void onApiLogEvent(ApiLogEvent event) {
      apiLogEvents.add(event);
    }

    List<ApiLogEvent> apiLogEvents() {
      return apiLogEvents;
    }
  }
}
