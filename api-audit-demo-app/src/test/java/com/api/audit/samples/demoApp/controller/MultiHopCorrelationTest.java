package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.audit.context.CorrelationContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/**
 * End-to-end proof that one correlation ID flows across an inbound request and an outbound call.
 *
 * <p>The test exercises the same path a real application uses: Spring MVC receives the request,
 * audit logging stores the incoming exchange, RestTemplate propagates {@code X-Correlation-ID}, and
 * the outbound exchange is stored with the same correlation ID.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(
    properties =
        "demo.downstream.status-url=https://downstream.example.org/api/v1/status?source=demo")
class MultiHopCorrelationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private RestTemplate demoRestTemplate;

  private MockRestServiceServer downstreamServer;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
    downstreamServer = MockRestServiceServer.bindTo(demoRestTemplate).build();
  }

  @Test
  void keepsSameCorrelationIdAcrossInboundAndOutboundAuditRecords() throws Exception {
    String correlationId = "corr-multi-hop-1";
    downstreamServer
        .expect(once(), requestTo("https://downstream.example.org/api/v1/status?source=demo"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(CorrelationContext.CORRELATION_ID_HEADER, correlationId))
        .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

    mockMvc
        .perform(get("/api/v1/multi-hop").header(CorrelationContext.CORRELATION_ID_HEADER, correlationId))
        .andExpect(status().isOk());

    downstreamServer.verify();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs =
                  jdbcTemplate.queryForList(
                      """
                      SELECT type, description, url, correlation_id
                      FROM api_audit_log
                      WHERE correlation_id = ?
                      ORDER BY timestamp
                      """,
                      correlationId);

              assertThat(logs).hasSize(2);
              assertThat(logs).extracting(row -> row.get("correlation_id")).containsOnly(correlationId);
              assertThat(logs).anySatisfy(this::assertIncomingMultiHopRecord);
              assertThat(logs).anySatisfy(this::assertOutgoingDownstreamRecord);
            });
  }

  private void assertIncomingMultiHopRecord(Map<String, Object> row) {
    assertThat(row.get("type")).isEqualTo("INCOMING");
    assertThat(row.get("description")).isEqualTo("MULTI_HOP_AUDIT");
    assertThat(row.get("url")).isEqualTo("/api/v1/multi-hop");
  }

  private void assertOutgoingDownstreamRecord(Map<String, Object> row) {
    assertThat(row.get("type")).isEqualTo("OUTGOING");
    assertThat(row.get("url"))
        .isEqualTo("https://downstream.example.org/api/v1/status?source=demo");
  }
}
