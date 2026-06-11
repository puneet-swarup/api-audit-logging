package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.audit.context.CorrelationContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end demo coverage for the outbound client reference endpoints.
 *
 * <p>The test uses a real local HTTP server so every client integration follows its normal runtime
 * path. Each request enters the demo app through Spring MVC, calls a downstream endpoint through one
 * client type, propagates the correlation ID, and stores an outbound audit record.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OutboundClientDemoIntegrationTest {

  private static MockWebServer downstreamServer;

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void startServer() throws IOException {
    downstreamServer = new MockWebServer();
    downstreamServer.start();
  }

  @AfterAll
  static void stopServer() throws IOException {
    downstreamServer.shutdown();
  }

  @DynamicPropertySource
  static void downstreamProperties(DynamicPropertyRegistry registry) {
    registry.add("demo.downstream.base-url", () -> downstreamServer.url("/").toString());
  }

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  void demonstratesAllOutboundClientIntegrations() throws Exception {
    assertOutboundDemo("/api/v1/demo/feign", "corr-demo-feign", "feign");
    assertOutboundDemo("/api/v1/demo/webclient", "corr-demo-webclient", "webclient");
    assertOutboundDemo("/api/v1/demo/restclient", "corr-demo-restclient", "restclient");
    assertOutboundDemo(
        "/api/v1/demo/http-interface", "corr-demo-http-interface", "http-interface");
  }

  private void assertOutboundDemo(String endpoint, String correlationId, String client)
      throws Exception {
    downstreamServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"ok\",\"client\":\"" + client + "\"}"));

    mockMvc
        .perform(get(endpoint).header(CorrelationContext.CORRELATION_ID_HEADER, correlationId))
        .andExpect(status().isOk());

    RecordedRequest request = downstreamServer.takeRequest(5, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/api/v1/status?client=" + client);
    assertThat(request.getHeader(CorrelationContext.CORRELATION_ID_HEADER))
        .isEqualTo(correlationId);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs =
                  jdbcTemplate.queryForList(
                      """
                      SELECT type, url, correlation_id
                      FROM api_audit_log
                      WHERE correlation_id = ?
                      ORDER BY timestamp
                      """,
                      correlationId);

              assertThat(logs).hasSize(2);
              assertThat(logs).anySatisfy(row -> assertThat(row.get("type")).isEqualTo("INCOMING"));
              assertThat(logs).anySatisfy(row -> assertOutboundRecord(row, client, correlationId));
            });
  }

  private void assertOutboundRecord(
      Map<String, Object> row, String client, String correlationId) {
    assertThat(row.get("type")).isEqualTo("OUTGOING");
    assertThat(row.get("correlation_id")).isEqualTo(correlationId);
    assertThat(row.get("url").toString()).contains("/api/v1/status?client=" + client);
  }
}
