package com.api.audit.examples.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that a custom {@code AuditPolicy} bean is discovered and can audit a request that no
 * annotation or path rule would capture.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("policy")
class HeaderBasedAuditPolicyTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.execute("DELETE FROM api_audit_log");
  }

  @Test
  @DisplayName("GIVEN X-Audit: on WHEN called THEN the custom policy audits and tags the record")
  void customPolicyAuditsWhenHeaderPresent() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Audit", "on");
    headers.set("X-Tenant", "acme");
    rest.exchange(
        "/examples/pathbased/skip",
        org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  jdbc.queryForList(
                      "SELECT * FROM api_audit_log WHERE url = ?", "/examples/pathbased/skip");
              assertThat(rows).isNotEmpty();
              assertThat(String.valueOf(rows.get(0).get("tags"))).contains("acme");
            });
  }
}
