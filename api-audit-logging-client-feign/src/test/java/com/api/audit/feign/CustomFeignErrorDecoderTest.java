package com.api.audit.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.util.JsonMasker;
import feign.Request;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for Feign error-response auditing.
 *
 * <p>The error decoder is important because Feign handles non-2xx responses outside the normal
 * logger success path. These tests prove that error bodies are captured, masked, and still
 * available to the delegate decoder that creates the application's exception.
 *
 * @author Puneet Swarup
 */
class CustomFeignErrorDecoderTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void capturesMasksAndRebuffersFeignErrorResponseBeforeDelegating() {
    CapturingPublisher publisher = new CapturingPublisher();
    AuditLoggingProperties properties = new AuditLoggingProperties();
    DelegatingDecoder delegate = new DelegatingDecoder();
    CustomFeignErrorDecoder decoder =
        new CustomFeignErrorDecoder(
            publisher, "feign-error-service", delegate, new JsonMasker(properties), properties);
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "https://example.org/payments",
            Map.of("Authorization", List.of("Bearer secret")),
            "{\"amount\":125}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            new feign.RequestTemplate());
    Response response =
        Response.builder()
            .request(request)
            .status(502)
            .reason("Bad Gateway")
            .headers(Map.of("Content-Type", List.of("application/json")))
            .body("{\"token\":\"secret-token\",\"message\":\"failed\"}", StandardCharsets.UTF_8)
            .build();

    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-feign-error-1");

    Exception exception = decoder.decode("PaymentClient#createPayment()", response);

    assertThat(exception).hasMessage("delegated");
    assertThat(delegate.bodySeenByDelegate).contains("secret-token");
    assertThat(publisher.apiLogEvents()).hasSize(1);

    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getServiceName()).isEqualTo("feign-error-service");
    assertThat(event.record().getType()).isEqualTo("OUTGOING_ERROR");
    assertThat(event.record().getMethod()).isEqualTo("POST");
    assertThat(event.record().getUrl()).isEqualTo("https://example.org/payments");
    assertThat(event.record().getRequestHeaders()).contains("\"Authorization\":[\"******\"]");
    assertThat(event.record().getResponseHeaders()).contains("content-type");
    assertThat(event.record().getHttpStatus()).isEqualTo(502);
    assertThat(event.record().getErrorType()).isEqualTo("HTTP_502");
    assertThat(event.record().getErrorMessage()).isEqualTo("Bad Gateway");
    assertThat(event.record().getCorrelationId()).isEqualTo("corr-feign-error-1");
    assertThat(event.record().getResponseBody()).contains("\"token\":\"******\"");
    assertThat(event.record().getResponseBody()).doesNotContain("secret-token");
  }

  @Test
  void capturesFeignErrorResponseWhenBodyIsMissing() {
    CapturingPublisher publisher = new CapturingPublisher();
    AuditLoggingProperties properties = new AuditLoggingProperties();
    DelegatingDecoder delegate = new DelegatingDecoder();
    CustomFeignErrorDecoder decoder =
        new CustomFeignErrorDecoder(
            publisher, "feign-error-service", delegate, new JsonMasker(properties), properties);
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "https://example.org/unavailable",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            new feign.RequestTemplate());
    Response response =
        Response.builder().request(request).status(503).reason("Service Unavailable").build();

    decoder.decode("PaymentClient#status()", response);

    assertThat(publisher.apiLogEvents()).hasSize(1);
    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getType()).isEqualTo("OUTGOING_ERROR");
    assertThat(event.record().getHttpStatus()).isEqualTo(503);
    assertThat(event.record().getErrorType()).isEqualTo("HTTP_503");
    assertThat(event.record().getResponseBody()).isEmpty();
  }

  static class DelegatingDecoder implements ErrorDecoder {

    private String bodySeenByDelegate;

    @Override
    public Exception decode(String methodKey, Response response) {
      try {
        bodySeenByDelegate = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
      } catch (Exception ex) {
        bodySeenByDelegate = ex.getMessage();
      }
      return new IllegalStateException("delegated");
    }
  }

  static class CapturingPublisher implements ApplicationEventPublisher {

    private final List<ApiLogEvent> apiLogEvents = new ArrayList<>();

    @Override
    public void publishEvent(ApplicationEvent event) {
      publishEvent((Object) event);
    }

    @Override
    public void publishEvent(Object event) {
      if (event instanceof ApiLogEvent apiLogEvent) {
        apiLogEvents.add(apiLogEvent);
      }
    }

    List<ApiLogEvent> apiLogEvents() {
      return apiLogEvents;
    }
  }
}
