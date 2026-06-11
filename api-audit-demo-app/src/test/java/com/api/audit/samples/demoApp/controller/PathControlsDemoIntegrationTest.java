package com.api.audit.samples.demoApp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Demo-profile test for inbound path include/exclude controls.
 *
 * @author Puneet Swarup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("path-controls")
class PathControlsDemoIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  void excludedDemoEndpointIsNotStored() throws Exception {
    mockMvc.perform(get("/api/v1/demo/no-audit/ping")).andExpect(status().isOk());

    await()
        .during(500, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM api_audit_log WHERE description = 'DEMO_EXCLUDED_FLOW'",
                            Integer.class))
                    .isZero());
  }
}
