package com.api.audit.examples.pathbased;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the documented behavior of every path-based rule option.
 *
 * <p>Each test corresponds to one rule in {@code application-pathbased.yaml} and asserts the
 * observable effect on stored records, so the examples are proven, not just described.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("pathbased")
class PathBasedRulesExampleTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.execute("DELETE FROM api_audit_log");
  }

  @Test
  @DisplayName("Ant rule: description and tags are stored, FULL capture keeps the body")
  void antRuleWithTagsAndFullCapture() {
    rest.getForEntity("/examples/pathbased/hello", String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  one("SELECT * FROM api_audit_log WHERE url = ?", "/examples/pathbased/hello");
              assertThat(row.get("description")).isEqualTo("Path rule - Ant + tags + FULL");
              assertThat(String.valueOf(row.get("tags"))).contains("path-rule");
              assertThat(row.get("response_body")).isNotNull();
            });
  }

  @Test
  @DisplayName("METADATA_ONLY rule: bodies are not stored")
  void metadataOnlyRuleStoresNoBody() {
    rest.getForEntity("/examples/pathbased/metadata", String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  one("SELECT * FROM api_audit_log WHERE url = ?", "/examples/pathbased/metadata");
              assertThat(row.get("description")).isEqualTo("Path rule - METADATA_ONLY");
              assertThat(row.get("response_body")).isNull();
            });
  }

  @Test
  @DisplayName("BODY_ONLY rule: headers are not stored but the body is")
  void bodyOnlyRuleStoresNoHeaders() {
    rest.postForEntity("/examples/pathbased/body-only", json("body-only-value"), String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  one("SELECT * FROM api_audit_log WHERE url = ?", "/examples/pathbased/body-only");
              assertThat(row.get("description")).isEqualTo("Path rule - BODY_ONLY");
              assertThat(row.get("request_headers")).isNull();
              assertThat(String.valueOf(row.get("request_body"))).contains("body-only-value");
            });
  }

  @Test
  @DisplayName("Method-scoped rule: POST is audited, DELETE on the same path is not")
  void methodScopedRule() {
    rest.postForEntity("/examples/pathbased/mutations", json("create"), String.class);
    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        count(
                            "SELECT COUNT(*) FROM api_audit_log WHERE url = ? AND method = 'POST'",
                            "/examples/pathbased/mutations"))
                    .isEqualTo(1));

    rest.delete("/examples/pathbased/mutations");
    sleep(1000);
    assertThat(
            count(
                "SELECT COUNT(*) FROM api_audit_log WHERE url = ? AND method = 'DELETE'",
                "/examples/pathbased/mutations"))
        .isZero();
  }

  @Test
  @DisplayName("Regex rule: versioned order path is audited with the regex tag")
  void regexRule() {
    rest.getForEntity("/examples/pathbased/orders/42", String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  one("SELECT * FROM api_audit_log WHERE url = ?", "/examples/pathbased/orders/42");
              assertThat(String.valueOf(row.get("tags"))).contains("regex");
            });
  }

  @Test
  @DisplayName("Explicit skip rule: the skip path is never audited")
  void skipRule() {
    rest.getForEntity("/examples/pathbased/skip", String.class);
    sleep(1000);
    assertThat(count("SELECT COUNT(*) FROM api_audit_log WHERE url = ?", "/examples/pathbased/skip"))
        .isZero();
  }

  private Map<String, Object> one(String sql, Object arg) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, arg);
    assertThat(rows).isNotEmpty();
    return rows.get(0);
  }

  private int count(String sql, Object arg) {
    Integer c = jdbc.queryForObject(sql, Integer.class, arg);
    return c == null ? 0 : c;
  }

  private HttpEntity<String> json(String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>("{\"message\":\"" + message + "\"}", headers);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
