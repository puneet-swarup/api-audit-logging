package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Micrometer audit metrics bridge.
 *
 * @author Puneet Swarup
 */
class MicrometerAuditMetricsTest {

  @Test
  @DisplayName(
      "GIVEN saved and failed records WHEN metrics recorded THEN low-cardinality meters exist")
  void recordsSavedAndFailedMeters() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MicrometerAuditMetrics metrics = new MicrometerAuditMetrics(registry);
    AuditLogRecord record =
        AuditLogRecord.builder()
            .serviceName("orders")
            .type("INCOMING")
            .method("POST")
            .url("/orders")
            .httpStatus(201)
            .correlationId("cid")
            .timestamp(LocalDateTime.now())
            .build();

    metrics.recordSaved(record, 12);
    metrics.recordFailure(record, new IllegalStateException("store down"));

    assertThat(registry.counter("api.audit.records.saved", "type", "INCOMING").count())
        .isEqualTo(1);
    assertThat(
            registry
                .counter(
                    "api.audit.records.failed",
                    "type",
                    "INCOMING",
                    "exception",
                    "IllegalStateException")
                .count())
        .isEqualTo(1);
    assertThat(registry.timer("api.audit.store.duration", "type", "INCOMING").count()).isEqualTo(1);
  }
}
