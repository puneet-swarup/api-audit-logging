package com.api.audit.storage.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.audit.model.AuditLogRecord;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for {@link KafkaAuditLogStore} retry and dead-letter behavior.
 *
 * @author Puneet Swarup
 */
@ExtendWith(MockitoExtension.class)
class KafkaAuditLogStoreTest {

  @Mock private KafkaTemplate<String, AuditLogRecord> kafkaTemplate;

  private AuditLogRecord record() {
    return AuditLogRecord.builder().type("INCOMING").correlationId("corr-1").build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static CompletableFuture completed() {
    return CompletableFuture.completedFuture(null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static CompletableFuture failed() {
    CompletableFuture future = new CompletableFuture();
    future.completeExceptionally(new RuntimeException("broker down"));
    return future;
  }

  @Test
  @DisplayName("GIVEN a successful send WHEN saved THEN it publishes to the primary topic")
  void publishesToPrimaryTopic() {
    when(kafkaTemplate.send(eq("audit"), any(), any())).thenReturn(completed());
    KafkaAuditLogStore store = new KafkaAuditLogStore(kafkaTemplate, "audit", null, 0, 0);

    store.save(record());

    verify(kafkaTemplate).send(eq("audit"), eq("corr-1"), any());
  }

  @Test
  @DisplayName("GIVEN a failing send with a DLQ WHEN retries exhausted THEN it routes to the DLQ")
  void routesToDeadLetter() {
    when(kafkaTemplate.send(eq("audit"), any(), any())).thenReturn(failed());
    when(kafkaTemplate.send(eq("audit-dlq"), any(), any())).thenReturn(completed());

    KafkaAuditLogStore store = new KafkaAuditLogStore(kafkaTemplate, "audit", "audit-dlq", 1, 0);

    store.save(record());

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(kafkaTemplate).send(eq("audit-dlq"), eq("corr-1"), any()));
    verify(kafkaTemplate, times(2)).send(eq("audit"), eq("corr-1"), any());
  }
}
