package com.api.audit.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.mask.JsonTreePayloadMasker;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import com.api.audit.spi.NoOpAuditMetrics;
import com.api.audit.util.JsonMasker;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApiLogListener}, with emphasis on the masking step not dropping fields.
 *
 * <p>The masking step used to rebuild the record field-by-field, which silently dropped any field
 * added later (this is exactly how the {@code tags} field was lost). These tests pin the guarantee
 * that masking preserves every field and only changes the payload bodies.
 *
 * @author Puneet Swarup
 */
class ApiLogListenerTest {

  private static final class CapturingStore implements AuditLogStore {
    final List<AuditLogRecord> saved = new ArrayList<>();

    @Override
    public void save(AuditLogRecord record) {
      saved.add(record);
    }
  }

  private ApiLogListener listener(CapturingStore store) {
    JsonMasker masker = new JsonMasker(new JsonTreePayloadMasker(new AuditLoggingProperties()));
    return new ApiLogListener(store, masker, new NoOpAuditMetrics());
  }

  private AuditLogRecord fullyPopulatedRecord() {
    return AuditLogRecord.builder()
        .serviceName("payments")
        .type("INCOMING")
        .method("POST")
        .description("Create payment")
        .url("/api/v1/payments")
        .queryString("source=mobile")
        .requestHeaders("{\"X-Tenant\":[\"acme\"]}")
        .responseHeaders("{\"Content-Type\":[\"application/json\"]}")
        .requestBody("{\"password\":\"secret\",\"amount\":100}")
        .responseBody("{\"status\":\"ok\"}")
        .httpStatus(201)
        .duration(42)
        .correlationId("corr-1")
        .clientIp("10.0.0.1")
        .userAgent("JUnit")
        .principalName("puneet")
        .timestamp(LocalDateTime.of(2026, 5, 25, 4, 42))
        .tags(Map.of("module", "payments", "tier", "critical"))
        .build();
  }

  @Test
  @DisplayName("GIVEN a fully populated record WHEN processed THEN every field reaches the store")
  void maskingPreservesAllFields() {
    CapturingStore store = new CapturingStore();
    ApiLogListener listener = listener(store);
    AuditLogRecord original = fullyPopulatedRecord();

    listener.handleLog(new ApiLogEvent(original));

    assertThat(store.saved).hasSize(1);
    AuditLogRecord masked = store.saved.get(0);

    assertThat(masked.getServiceName()).isEqualTo(original.getServiceName());
    assertThat(masked.getType()).isEqualTo(original.getType());
    assertThat(masked.getMethod()).isEqualTo(original.getMethod());
    assertThat(masked.getDescription()).isEqualTo(original.getDescription());
    assertThat(masked.getUrl()).isEqualTo(original.getUrl());
    assertThat(masked.getQueryString()).isEqualTo(original.getQueryString());
    assertThat(masked.getRequestHeaders()).isEqualTo(original.getRequestHeaders());
    assertThat(masked.getResponseHeaders()).isEqualTo(original.getResponseHeaders());
    assertThat(masked.getHttpStatus()).isEqualTo(original.getHttpStatus());
    assertThat(masked.getDuration()).isEqualTo(original.getDuration());
    assertThat(masked.getCorrelationId()).isEqualTo(original.getCorrelationId());
    assertThat(masked.getClientIp()).isEqualTo(original.getClientIp());
    assertThat(masked.getUserAgent()).isEqualTo(original.getUserAgent());
    assertThat(masked.getPrincipalName()).isEqualTo(original.getPrincipalName());
    assertThat(masked.getTimestamp()).isEqualTo(original.getTimestamp());
    assertThat(masked.getTags()).containsEntry("module", "payments");
  }

  @Test
  @DisplayName(
      "GIVEN sensitive fields WHEN processed THEN bodies are redacted but other fields intact")
  void maskingRedactsBodiesOnly() {
    CapturingStore store = new CapturingStore();
    ApiLogListener listener = listener(store);

    listener.handleLog(new ApiLogEvent(fullyPopulatedRecord()));

    AuditLogRecord masked = store.saved.get(0);
    assertThat(masked.getRequestBody()).doesNotContain("secret").contains("******");
    assertThat(masked.getResponseBody()).contains("ok");
    assertThat(masked.getTags()).containsEntry("tier", "critical");
  }
}
