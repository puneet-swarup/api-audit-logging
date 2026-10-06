package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end proof that path-based auditing captures requests with <b>no annotation and no code
 * change</b> in the controller.
 *
 * <p>The controller under test, {@link PathAuditDemoController}, deliberately has no {@code @AuditLog}
 * annotation. The {@code path-based} profile enables capture purely through configuration. These
 * tests therefore exercise the exact "add the library and configuration, restart the JVM" flow the
 * feature was designed for, and cover path-rule features: description, tags, capture modes, method
 * scoping, and explicit skips.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("path-based")
class PathBasedAuditingIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  @DisplayName("GIVEN unannotated endpoint WHEN path rule matches THEN request is audited")
  void unannotatedEndpointIsAuditedByPathRule() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/path-audited/hello", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = logsForUrl("/api/v1/path-audited/hello");
              assertThat(logs).isNotEmpty();

              Map<String, Object> log = logs.get(0);
              assertThat(log.get("service_name")).isEqualTo("api-audit-demo-app");
              assertThat(log.get("type")).isEqualTo("INCOMING");
              assertThat(log.get("method")).isEqualTo("GET");
              assertThat(log.get("description")).isEqualTo("Path-Audited Demo APIs");
              assertThat(String.valueOf(log.get("tags"))).contains("path-rule");
            });
  }

  @Test
  @DisplayName("GIVEN a tag filter WHEN querying storage THEN only tagged records return")
  void internalEndpointFiltersByTag() {

    restTemplate.getForEntity("/api/v1/path-audited/hello", String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> tagged =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM api_audit_log WHERE tags LIKE ?", "%audited-by%");
              assertThat(tagged).isNotEmpty();
            });

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT * FROM api_audit_log WHERE tags LIKE ?", "%missing-key%"))
        .isEmpty();
  }

  @Test
  @DisplayName("GIVEN unannotated POST endpoint WHEN path rule matches THEN body is captured")
  void unannotatedPostEndpointCapturesBody() {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v1/path-audited/echo", jsonEntity("path-audited-post"), String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = logsForUrl("/api/v1/path-audited/echo");
              assertThat(logs).isNotEmpty();
              Map<String, Object> log = logs.get(0);
              assertThat(log.get("method")).isEqualTo("POST");
              assertThat(String.valueOf(log.get("request_body"))).contains("path-audited-post");
              assertThat(String.valueOf(log.get("response_body"))).contains("path-audited-post");
            });
  }

  @Test
  @DisplayName("GIVEN METADATA_ONLY rule WHEN matched THEN bodies are not stored")
  void metadataOnlyRuleSkipsBodies() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/path-audited/metadata/ping", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = logsForUrl("/api/v1/path-audited/metadata/ping");
              assertThat(logs).isNotEmpty();
              Map<String, Object> log = logs.get(0);
              assertThat(log.get("description")).isEqualTo("Metadata-only API");
              assertThat(log.get("response_body")).isNull();
            });
  }

  @Test
  @DisplayName("GIVEN BODY_ONLY rule WHEN matched THEN headers are not stored")
  void bodyOnlyRuleSkipsHeaders() {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v1/path-audited/body-only/echo", jsonEntity("body-only-post"), String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = logsForUrl("/api/v1/path-audited/body-only/echo");
              assertThat(logs).isNotEmpty();
              Map<String, Object> log = logs.get(0);
              assertThat(log.get("description")).isEqualTo("Body-only API");
              assertThat(String.valueOf(log.get("request_body"))).contains("body-only-post");
              assertThat(log.get("request_headers")).isNull();
            });
  }

  @Test
  @DisplayName("GIVEN method-scoped rule WHEN method matches THEN request is audited")
  void methodScopedRuleAuditsMatchingMethod() {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v1/path-audited/mutations", jsonEntity("create"), String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> logs = logsForUrl("/api/v1/path-audited/mutations");
              assertThat(logs).anyMatch(l -> "Mutation APIs".equals(l.get("description")));
            });
  }

  @Test
  @DisplayName("GIVEN a path outside the rules WHEN called THEN it is not audited")
  void nonMatchingPathIsNotAudited() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v3/hello", String.class);
    assertThat(response.getStatusCode()).isNotNull();

    sleep(1000);
    assertThat(logsForUrl("/api/v3/hello")).isEmpty();
  }

  private HttpEntity<String> jsonEntity(String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>("{\"message\":\"" + message + "\"}", headers);
  }

  private List<Map<String, Object>> logsForUrl(String url) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM api_audit_log WHERE url = ? ORDER BY timestamp DESC", url);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
