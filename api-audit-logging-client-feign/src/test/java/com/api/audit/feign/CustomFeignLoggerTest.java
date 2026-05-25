package com.api.audit.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.annotation.AuditLog;
import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import feign.Contract;
import feign.MethodMetadata;
import feign.Request;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.io.IOException;
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
 * Unit tests for the Feign logger used as the outbound audit capture point.
 *
 * <p>Feign exposes request and response bodies to custom loggers only at {@code FULL} logging
 * level. These tests exercise the logger directly so the behavior remains clear without starting a
 * web server: annotated client methods are audited, correlation IDs are copied from MDC, and the
 * response body is re-buffered for normal Feign decoding.
 *
 * @author Puneet Swarup
 */
class CustomFeignLoggerTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void publishesAuditEventForAnnotatedFeignMethodAndRebuffersResponse() throws IOException {
    CapturingPublisher publisher = new CapturingPublisher();
    TestableFeignLogger logger =
        new TestableFeignLogger(publisher, "feign-test-service", new AuditLoggingProperties());
    MethodMetadata metadata = feignMetadataFor("createPayment");
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "https://example.org/payments",
            Map.of("Authorization", List.of("Bearer secret")),
            "{\"amount\":125}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            requestTemplate(metadata));
    Response response =
        Response.builder()
            .request(request)
            .status(201)
            .reason("Created")
            .headers(Map.of("X-Result", List.of("created")))
            .body("{\"id\":\"pay-1\"}", StandardCharsets.UTF_8)
            .build();

    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-feign-1");

    Response rebuffered = logger.capture("PaymentClient#createPayment()", response, 42);

    assertThat(publisher.apiLogEvents()).hasSize(1);
    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getServiceName()).isEqualTo("feign-test-service");
    assertThat(event.record().getType()).isEqualTo("OUTGOING");
    assertThat(event.record().getMethod()).isEqualTo("POST");
    assertThat(event.record().getUrl()).isEqualTo("https://example.org/payments");
    assertThat(event.record().getRequestHeaders()).contains("\"Authorization\":[\"******\"]");
    assertThat(event.record().getRequestHeaders()).doesNotContain("Bearer secret");
    assertThat(event.record().getResponseHeaders()).contains("x-result");
    assertThat(event.record().getHttpStatus()).isEqualTo(201);
    assertThat(event.record().getDuration()).isEqualTo(42);
    assertThat(event.record().getRequestBody()).isEqualTo("{\"amount\":125}");
    assertThat(event.record().getResponseBody()).isEqualTo("{\"id\":\"pay-1\"}");
    assertThat(event.record().getCorrelationId()).isEqualTo("corr-feign-1");
    assertThat(Util.toString(rebuffered.body().asReader(StandardCharsets.UTF_8)))
        .isEqualTo("{\"id\":\"pay-1\"}");
  }

  @Test
  void ignoresFeignMethodWithoutAuditAnnotation() throws IOException {
    CapturingPublisher publisher = new CapturingPublisher();
    TestableFeignLogger logger =
        new TestableFeignLogger(publisher, "feign-test-service", new AuditLoggingProperties());
    MethodMetadata metadata = feignMetadataFor("unAuditedHealth");
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "https://example.org/health",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            requestTemplate(metadata));
    Response response =
        Response.builder()
            .request(request)
            .status(200)
            .reason("OK")
            .body("{\"status\":\"up\"}", StandardCharsets.UTF_8)
            .build();

    Response returned = logger.capture("PaymentClient#unAuditedHealth()", response, 7);

    assertThat(returned).isSameAs(response);
    assertThat(publisher.apiLogEvents()).isEmpty();
  }

  @Test
  void publishesAuditEventForAnnotatedFeignMethodWithEmptyResponseBody() throws IOException {
    CapturingPublisher publisher = new CapturingPublisher();
    TestableFeignLogger logger =
        new TestableFeignLogger(publisher, "feign-test-service", new AuditLoggingProperties());
    MethodMetadata metadata = feignMetadataFor("createPayment");
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "https://example.org/payments",
            Map.of(),
            "{\"amount\":125}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            requestTemplate(metadata));
    Response response =
        Response.builder().request(request).status(204).reason("No Content").build();

    Response returned = logger.capture("PaymentClient#createPayment()", response, 10);

    assertThat(returned).isSameAs(response);
    assertThat(publisher.apiLogEvents()).hasSize(1);
    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getHttpStatus()).isEqualTo(204);
    assertThat(event.record().getResponseBody()).isEmpty();
  }

  private MethodMetadata feignMetadataFor(String methodName) {
    return new Contract.Default()
        .parseAndValidateMetadata(PaymentClient.class).stream()
            .filter(metadata -> metadata.method().getName().equals(methodName))
            .findFirst()
            .orElseThrow();
  }

  private RequestTemplate requestTemplate(MethodMetadata metadata) {
    RequestTemplate template = new RequestTemplate();
    template.methodMetadata(metadata);
    return template;
  }

  interface PaymentClient {

    @AuditLog("CREATE_PAYMENT")
    @RequestLine("POST /payments")
    String createPayment(String request);

    @RequestLine("GET /health")
    String unAuditedHealth();
  }

  static class TestableFeignLogger extends CustomFeignLogger {

    TestableFeignLogger(
        ApplicationEventPublisher publisher, String appName, AuditLoggingProperties properties) {
      super(publisher, appName, properties);
    }

    Response capture(String configKey, Response response, long elapsedTime) throws IOException {
      return logAndRebufferResponse(configKey, Level.FULL, response, elapsedTime);
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
