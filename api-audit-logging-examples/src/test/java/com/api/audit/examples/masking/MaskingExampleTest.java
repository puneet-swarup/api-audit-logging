package com.api.audit.examples.masking;

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
 * Verifies masking: built-in fields, configured additional fields, nested objects, and the custom
 * email-scrubbing masker are all applied before storage.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("masking")
class MaskingExampleTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.execute("DELETE FROM api_audit_log");
  }

  @Test
  @DisplayName("Built-in, configured, and custom email masking are all applied")
  void maskingRedactsSensitiveFields() {
    String body =
        "{\"amount\":100,"
            + "\"card\":{\"number\":\"4111111111111111\",\"cvv\":\"123\"},"
            + "\"customer\":{\"email\":\"jane@example.com\",\"password\":\"hunter2\"},"
            + "\"otp\":\"9876\"}";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    rest.postForEntity(
        "/examples/masking/payment", new HttpEntity<>(body, headers), String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbc.queryForList(
                      "SELECT * FROM api_audit_log WHERE url = ?", "/examples/masking/payment");
              assertThat(rows).isNotEmpty();
              String requestBody = String.valueOf(rows.get(0).get("request_body"));
              // Built-in: cvv and password masked anywhere in the tree.
              assertThat(requestBody).doesNotContain("123").doesNotContain("hunter2");
              // Configured additional field: otp.
              assertThat(requestBody).doesNotContain("9876");
              // Custom masker: email scrubbed even though the field name is not sensitive.
              assertThat(requestBody).doesNotContain("jane@example.com");
              // Non-sensitive value survives.
              assertThat(requestBody).contains("100");
            });
  }
}
