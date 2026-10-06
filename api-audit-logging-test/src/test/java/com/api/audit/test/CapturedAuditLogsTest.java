package com.api.audit.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CapturedAuditLogs} and {@link AuditLogAssertions}.
 *
 * @author Puneet Swarup
 */
class CapturedAuditLogsTest {

  private CapturedAuditLogs logs;

  @BeforeEach
  void setUp() {
    logs = new CapturedAuditLogs();
  }

  private AuditLogRecord record(String type, String method, Integer status) {
    return AuditLogRecord.builder()
        .type(type)
        .method(method)
        .httpStatus(status)
        .url("/api/v1/payments")
        .correlationId("corr-1")
        .requestBody("{\"amount\":100}")
        .tags(Map.of("module", "payments"))
        .build();
  }

  @Test
  @DisplayName("GIVEN no events WHEN queried THEN empty")
  void startsEmpty() {
    assertThat(logs.isEmpty()).isTrue();
    assertThat(logs.count()).isZero();
  }

  @Test
  @DisplayName("GIVEN an event WHEN captured THEN count and content reflect it")
  void capturesEvents() {
    logs.onAuditEvent(new ApiLogEvent(record("INCOMING", "GET", 200)));
    assertThat(logs.count()).isEqualTo(1);
    assertThat(logs.anyMatch(r -> "INCOMING".equals(r.getType()))).isTrue();
  }

  @Test
  @DisplayName("GIVEN one record WHEN single THEN chained assertions pass")
  void singleAssertions() {
    logs.onAuditEvent(new ApiLogEvent(record("INCOMING", "POST", 202)));
    logs.single()
        .hasType("INCOMING")
        .hasMethod("POST")
        .hasStatus(202)
        .urlContains("/payments")
        .hasCorrelationId("corr-1")
        .hasTag("module", "payments")
        .requestBodyContains("amount");
  }

  @Test
  @DisplayName("GIVEN a failed assertion WHEN evaluated THEN AssertionError is thrown")
  void failedAssertionThrows() {
    logs.onAuditEvent(new ApiLogEvent(record("INCOMING", "GET", 200)));
    assertThatThrownBy(() -> logs.single().hasType("OUTGOING")).isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("GIVEN more than one record WHEN single called THEN AssertionError")
  void singleFailsWhenMultiple() {
    logs.onAuditEvent(new ApiLogEvent(record("INCOMING", "GET", 200)));
    logs.onAuditEvent(new ApiLogEvent(record("OUTGOING", "GET", 200)));
    assertThatThrownBy(logs::single).isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("GIVEN an empty capture WHEN first called THEN AssertionError")
  void firstFailsWhenEmpty() {
    assertThatThrownBy(logs::first).isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("GIVEN masked body WHEN asserting absence THEN passes")
  void absenceAssertion() {
    AuditLogRecord masked =
        AuditLogRecord.builder().requestBody("{\"password\":\"******\"}").build();
    logs.onAuditEvent(new ApiLogEvent(masked));
    logs.single().requestBodyDoesNotContain("secret");
  }

  @Test
  @DisplayName("GIVEN clear WHEN called THEN capture is emptied")
  void clearWorks() {
    logs.onAuditEvent(new ApiLogEvent(record("INCOMING", "GET", 200)));
    logs.clear();
    assertThat(logs.isEmpty()).isTrue();
  }
}
