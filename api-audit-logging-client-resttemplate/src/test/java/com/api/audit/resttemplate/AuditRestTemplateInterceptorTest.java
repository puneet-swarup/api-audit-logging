package com.api.audit.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for outbound blocking HTTP audit interception.
 *
 * <p>The interceptor is exercised through a real {@link RestTemplate} and a mock HTTP server so the
 * test can verify the behavior application developers care about: correlation propagation, body
 * capture, status capture, and response re-buffering.
 *
 * @author Puneet Swarup
 */
class AuditRestTemplateInterceptorTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void capturesOutboundExchangeAndKeepsResponseReadable() {
    CapturingPublisher publisher = new CapturingPublisher();
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getInterceptors()
        .add(
            new AuditRestTemplateInterceptor(
                publisher, "blocking-test-service", new AuditLoggingProperties()));

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(once(), requestTo("https://example.org/payments?source=mobile"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(CorrelationContext.CORRELATION_ID_HEADER, "corr-resttemplate-1"))
        .andExpect(header("Authorization", "Bearer secret"))
        .andExpect(content().json("{\"amount\":125}"))
        .andRespond(withSuccess("{\"status\":\"accepted\"}", MediaType.APPLICATION_JSON));

    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-resttemplate-1");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("secret");
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>("{\"amount\":125}", headers);

    String response =
        restTemplate.postForObject(
            "https://example.org/payments?source=mobile", entity, String.class);

    server.verify();
    assertThat(response).isEqualTo("{\"status\":\"accepted\"}");
    assertThat(publisher.apiLogEvents()).hasSize(1);

    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getServiceName()).isEqualTo("blocking-test-service");
    assertThat(event.record().getType()).isEqualTo("OUTGOING");
    assertThat(event.record().getMethod()).isEqualTo("POST");
    assertThat(event.record().getUrl()).isEqualTo("https://example.org/payments?source=mobile");
    assertThat(event.record().getQueryString()).isEqualTo("source=mobile");
    assertThat(event.record().getRequestHeaders()).contains("\"Authorization\":[\"******\"]");
    assertThat(event.record().getRequestHeaders()).doesNotContain("Bearer secret");
    assertThat(event.record().getResponseHeaders()).contains("Content-Type");
    assertThat(event.record().getRequestBody()).isEqualTo("{\"amount\":125}");
    assertThat(event.record().getResponseBody()).isEqualTo("{\"status\":\"accepted\"}");
    assertThat(event.record().getHttpStatus()).isEqualTo(200);
    assertThat(event.record().getCorrelationId()).isEqualTo("corr-resttemplate-1");
  }

  @Test
  void respectsConfiguredBodyLimit() {
    AuditLoggingProperties properties = new AuditLoggingProperties();
    properties.getCapture().setMaxBodySize(5);
    CapturingPublisher publisher = new CapturingPublisher();
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getInterceptors()
        .add(new AuditRestTemplateInterceptor(publisher, "blocking-test-service", properties));

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(once(), requestTo("https://example.org/large"))
        .andRespond(withSuccess("response-body-too-large", MediaType.TEXT_PLAIN));

    restTemplate.postForObject("https://example.org/large", "request-body-too-large", String.class);

    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getRequestBody()).startsWith("[BODY TOO LARGE:");
    assertThat(event.record().getResponseBody()).startsWith("[BODY TOO LARGE:");
  }

  @Test
  void capturesErrorResponseBeforeRestTemplateThrows() {
    CapturingPublisher publisher = new CapturingPublisher();
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getInterceptors()
        .add(
            new AuditRestTemplateInterceptor(
                publisher, "blocking-test-service", new AuditLoggingProperties()));

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(once(), requestTo("https://example.org/failing"))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"downstream failed\"}"));

    assertThatThrownBy(() -> restTemplate.getForObject("https://example.org/failing", String.class))
        .isInstanceOf(org.springframework.web.client.HttpStatusCodeException.class);

    server.verify();
    assertThat(publisher.apiLogEvents()).hasSize(1);
    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getType()).isEqualTo("OUTGOING");
    assertThat(event.record().getHttpStatus()).isEqualTo(502);
    assertThat(event.record().getResponseBody()).contains("downstream failed");
  }

  @Test
  void capturesTransportErrorBeforeRethrowing() {
    CapturingPublisher publisher = new CapturingPublisher();
    AuditRestTemplateInterceptor interceptor =
        new AuditRestTemplateInterceptor(
            publisher, "blocking-test-service", new AuditLoggingProperties());
    MockClientHttpRequest request =
        new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("https://example.org/down"));

    assertThatThrownBy(
            () ->
                interceptor.intercept(
                    request,
                    new byte[0],
                    (httpRequest, body) -> {
                      throw new ResourceAccessException("connection refused");
                    }))
        .isInstanceOf(ResourceAccessException.class);

    assertThat(publisher.apiLogEvents()).hasSize(1);
    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getType()).isEqualTo("OUTGOING_TRANSPORT_ERROR");
    assertThat(event.record().getHttpStatus()).isNull();
    assertThat(event.record().getErrorType()).isEqualTo(ResourceAccessException.class.getName());
    assertThat(event.record().getErrorMessage()).contains("connection refused");
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
