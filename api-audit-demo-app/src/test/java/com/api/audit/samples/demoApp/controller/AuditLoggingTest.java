package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AuditLoggingTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  void testAuditLogPersistenceMethod() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/hello?name=Puneet", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = findIncomingLogsByDescription("METHOD_AUDIT");
              assertThat(logs).isNotEmpty();

              Map<String, Object> lastLog = logs.get(0);
              assertThat(lastLog.get("service_name")).isEqualTo("api-audit-demo-app");
              assertThat(lastLog.get("type")).isEqualTo("INCOMING");
              assertThat(lastLog.get("method")).isEqualTo("GET");
              assertThat(lastLog.get("description")).isEqualTo("METHOD_AUDIT");
              assertThat(lastLog.get("url")).isEqualTo("/api/v1/hello");
              assertThat(lastLog.get("request_body")).isNotNull();
              assertThat(lastLog.get("response_body")).isNotNull();
              assertThat(lastLog.get("http_status")).isEqualTo(200);
              assertThat(lastLog.get("duration")).isNotNull();
              assertThat(lastLog.get("correlation_id")).isNotNull();
              assertThat(lastLog.get("timestamp")).isNotNull();
              assertThat(lastLog.get("id")).isNotNull();
            });
  }

  @Test
  void testAuditLogPersistenceClass() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v2/hello?name=Puneet", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = findIncomingLogsByDescription("CLASS_AUDIT");
              assertThat(logs).isNotEmpty();

              Map<String, Object> lastLog = logs.get(0);
              assertThat(lastLog.get("service_name")).isEqualTo("api-audit-demo-app");
              assertThat(lastLog.get("type")).isEqualTo("INCOMING");
              assertThat(lastLog.get("method")).isEqualTo("GET");
              assertThat(lastLog.get("description")).isEqualTo("CLASS_AUDIT");
              assertThat(lastLog.get("url")).isEqualTo("/api/v2/hello");
              assertThat(lastLog.get("request_body")).isNotNull();
              assertThat(lastLog.get("response_body")).isNotNull();
              assertThat(lastLog.get("http_status")).isEqualTo(200);
              assertThat(lastLog.get("duration")).isNotNull();
              assertThat(lastLog.get("correlation_id")).isNotNull();
              assertThat(lastLog.get("timestamp")).isNotNull();
              assertThat(lastLog.get("id")).isNotNull();
            });
  }

  @Test
  void testAuditLogPersistenceForPostBody() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>("{\"message\":\"hello-post\"}", headers);

    ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/echo", entity, String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = findIncomingLogsByDescription("METHOD_POST_AUDIT");
              assertThat(logs).isNotEmpty();

              Map<String, Object> lastLog = logs.get(0);
              assertThat(lastLog.get("description")).isEqualTo("METHOD_POST_AUDIT");
              assertThat(lastLog.get("method")).isEqualTo("POST");
              assertThat(lastLog.get("url")).isEqualTo("/api/v1/echo");
              assertThat(lastLog.get("request_body").toString()).contains("hello-post");
              assertThat(lastLog.get("response_body").toString()).contains("hello-post");
            });
  }

  @Test
  void whenApiIsHit_thenLogIsRetrievableViaInternalApi() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/hello?name=Puneet")
                .header("X-Correlation-Id", "test-123")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.2")
                .header("User-Agent", "AuditTest/1.0")
                .header("Authorization", "Bearer should-not-be-stored")
                .header("X-Audit-Api-Key", "dev-only-key"))
        .andExpect(status().isOk());

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                mockMvc
                    .perform(
                        get("/internal/audit-logs")
                            .header("X-Audit-Api-Key", "dev-only-key")
                            .param("correlationId", "test-123")
                            .param("type", "INCOMING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].serviceName").value("api-audit-demo-app"))
                    .andExpect(jsonPath("$.content[0].responseBody").value(containsString("Puneet")))
                    .andExpect(jsonPath("$.content[0].queryString").value("name=Puneet"))
                    .andExpect(jsonPath("$.content[0].clientIp").value("203.0.113.10"))
                    .andExpect(jsonPath("$.content[0].userAgent").value("AuditTest/1.0"))
                    .andExpect(
                        jsonPath("$.content[0].requestHeaders")
                            .value(containsString("\"Authorization\":[\"******\"]")))
                    .andExpect(
                        jsonPath("$.content[0].requestHeaders")
                            .value(org.hamcrest.Matchers.not(containsString("should-not-be-stored")))));
  }

  private List<Map<String, Object>> findIncomingLogsByDescription(String description) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM api_audit_log WHERE type = 'INCOMING' AND description = ? ORDER BY timestamp DESC",
        description);
  }
}
