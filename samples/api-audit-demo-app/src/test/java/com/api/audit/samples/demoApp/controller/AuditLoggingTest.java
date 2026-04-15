package com.api.audit.samples.demoApp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuditLoggingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testAuditLogPersistenceMethod() {
        // 1. Call the API
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hello?name=Puneet", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Wait for Async Persistence
        // Since logging is usually async, we wait up to 2 seconds for the record to appear
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT * FROM api_audit_log");
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
        // 1. Call the API
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v2/hello?name=Puneet", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Wait for Async Persistence
        // Since logging is usually async, we wait up to 2 seconds for the record to appear
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT * FROM api_audit_log");
            assertThat(logs).isNotEmpty();

            Map<String, Object> lastLog = logs.get(1);
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
}