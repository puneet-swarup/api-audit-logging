package com.api.audit.webclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration tests for {@link WebClientAuditFilter} against a real (mock) HTTP server.
 *
 * <p>These prove two things that matter for production: the audit record is produced with the right
 * fields, and enabling body capture does <b>not</b> break the caller — the response body is still
 * delivered downstream intact.
 *
 * @author Puneet Swarup
 */
class WebClientAuditFilterTest {

  private MockWebServer server;
  private final List<ApiLogEvent> events = new ArrayList<>();
  private final ApplicationEventPublisher publisher = event -> events.add((ApiLogEvent) event);

  private WebClient client(boolean captureBodies) {
    AuditLoggingProperties properties = new AuditLoggingProperties();
    properties.getWebclient().setCaptureBodies(captureBodies);
    WebClientAuditFilter filter = new WebClientAuditFilter(publisher, "test-service", properties);
    return WebClient.builder().baseUrl(server.url("/").toString()).filter(filter.filter()).build();
  }

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    events.clear();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("GIVEN metadata-only mode WHEN a GET succeeds THEN an OUTGOING record is published")
  void metadataOnlyPublishesOutgoing() {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    String body = client(false).get().uri("/thing").retrieve().bodyToMono(String.class).block();

    assertThat(body).isEqualTo("{\"ok\":true}");
    assertThat(events).hasSize(1);
    AuditLogRecord record = events.get(0).record();
    assertThat(record.getType()).isEqualTo("OUTGOING");
    assertThat(record.getServiceName()).isEqualTo("test-service");
    assertThat(record.getMethod()).isEqualTo("GET");
    assertThat(record.getHttpStatus()).isEqualTo(200);
    assertThat(record.getUrl()).contains("/thing");
  }

  @Test
  @DisplayName(
      "GIVEN body capture WHEN a GET succeeds THEN the response body is captured AND delivered")
  void bodyCapturePreservesDownstreamBody() {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"message\":\"hi\"}"));

    String body = client(true).get().uri("/thing").retrieve().bodyToMono(String.class).block();

    // The caller still receives the full body.
    assertThat(body).isEqualTo("{\"message\":\"hi\"}");
    // And the audit record contains it.
    assertThat(events).hasSize(1);
    assertThat(events.get(0).record().getResponseBody()).isEqualTo("{\"message\":\"hi\"}");
  }

  @Test
  @DisplayName(
      "GIVEN a transport failure WHEN exchange fails THEN an OUTGOING_TRANSPORT_ERROR is published")
  void transportErrorIsCaptured() throws Exception {
    server.shutdown();
    try {
      client(false).get().uri("/thing").retrieve().bodyToMono(String.class).block();
    } catch (RuntimeException expected) {
      // expected: server is down
    }
    assertThat(events).hasSize(1);
    AuditLogRecord record = events.get(0).record();
    assertThat(record.getType()).isEqualTo("OUTGOING_TRANSPORT_ERROR");
    assertThat(record.getErrorType()).isNotBlank();
  }
}
