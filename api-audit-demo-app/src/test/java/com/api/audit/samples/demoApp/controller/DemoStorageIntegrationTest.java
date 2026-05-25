package com.api.audit.samples.demoApp.controller;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.audit.storage.memory.InMemoryAuditLogStore;
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
 * End-to-end demo tests for searchable storage profiles beyond the default JPA path.
 *
 * <p>These tests intentionally exercise the public demo endpoints and then query the internal audit
 * endpoint. That keeps the demo app useful as living documentation for developers comparing the
 * built-in storage options.
 *
 * @author Puneet Swarup
 */
class DemoStorageIntegrationTest {}

/** Verifies the demo can capture and search audit records with plain JDBC storage. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("jdbc")
class JdbcDemoStorageIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.execute("DELETE FROM api_audit_log");
  }

  @Test
  void capturesIncomingRequestAndSearchesItThroughJdbcProfile() throws Exception {
    performAuditedRequest(mockMvc, "corr-demo-jdbc", "198.51.100.20");

    assertAuditRecordIsSearchable(mockMvc, "corr-demo-jdbc", "198.51.100.20");
  }

  private void performAuditedRequest(MockMvc mockMvc, String correlationId, String clientIp)
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/hello?name=JdbcProfile")
                .header("X-Correlation-Id", correlationId)
                .header("X-Forwarded-For", clientIp)
                .header("User-Agent", "JdbcDemoTest/1.0")
                .header("X-Audit-Api-Key", "dev-only-key"))
        .andExpect(status().isOk());
  }

  private void assertAuditRecordIsSearchable(MockMvc mockMvc, String correlationId, String clientIp) {
    await()
        .atMost(8, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                mockMvc
                    .perform(
                        get("/internal/audit-logs")
                            .header("X-Audit-Api-Key", "dev-only-key")
                            .param("correlationId", correlationId)
                            .param("serviceName", "api-audit-demo-app")
                            .param("method", "GET")
                            .param("httpStatus", "200")
                            .param("clientIp", clientIp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].type").value("INCOMING"))
                    .andExpect(jsonPath("$.content[0].queryString").value("name=JdbcProfile"))
                    .andExpect(
                        jsonPath("$.content[0].responseBody").value(containsString("JdbcProfile"))));
  }
}

/** Verifies the demo can capture and search audit records with in-memory storage. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class MemoryDemoStorageIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private InMemoryAuditLogStore inMemoryAuditLogStore;

  @BeforeEach
  void clearStore() {
    inMemoryAuditLogStore.clear();
  }

  @Test
  void capturesIncomingRequestAndSearchesItThroughMemoryProfile() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/hello?name=MemoryProfile")
                .header("X-Correlation-Id", "corr-demo-memory")
                .header("X-Forwarded-For", "198.51.100.30")
                .header("User-Agent", "MemoryDemoTest/1.0")
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
                            .param("correlationId", "corr-demo-memory")
                            .param("serviceName", "api-audit-demo-app")
                            .param("method", "GET")
                            .param("httpStatus", "200")
                            .param("clientIp", "198.51.100.30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].type").value("INCOMING"))
                    .andExpect(jsonPath("$.content[0].queryString").value("name=MemoryProfile"))
                    .andExpect(
                        jsonPath("$.content[0].responseBody")
                            .value(containsString("MemoryProfile"))));
  }
}
