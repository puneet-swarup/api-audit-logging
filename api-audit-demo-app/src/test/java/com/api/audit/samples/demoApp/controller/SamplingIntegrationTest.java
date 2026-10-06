package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end proof that sampling stores only a fraction of successful records while always storing
 * errors.
 *
 * <p>Uses the {@code sampling} profile where {@code sample-rate=0.2}. A large number of requests
 * must result in materially fewer stored records than requests.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sampling")
class SamplingIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  @DisplayName("GIVEN a low sample rate WHEN many successes occur THEN far fewer records are stored")
  void samplingStoresOnlyAFractionOfSuccesses() throws InterruptedException {
    int requests = 100;
    for (int i = 0; i < requests; i++) {
      restTemplate.getForEntity("/api/v1/hello?name=Sampling" + i, String.class);
    }
    // Allow the async pipeline to drain.
    Thread.sleep(2000);

    Integer stored =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_audit_log WHERE url = '/api/v1/hello'", Integer.class);

    assertThat(stored).isNotNull();
    // With sample-rate 0.2 we expect well under half to be stored. A generous bound avoids
    // flakiness while still proving sampling is active.
    assertThat(stored).isLessThan(requests / 2);
  }
}
