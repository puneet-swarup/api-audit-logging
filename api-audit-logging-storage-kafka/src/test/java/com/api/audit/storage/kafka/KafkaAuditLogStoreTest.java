package com.api.audit.storage.kafka;

import static org.mockito.Mockito.verify;

import com.api.audit.model.AuditLogRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for the Kafka audit sink.
 *
 * @author Puneet Swarup
 */
class KafkaAuditLogStoreTest {

  @SuppressWarnings("unchecked")
  @Test
  void savePublishesRecordUsingCorrelationIdAsKey() {
    KafkaTemplate<String, AuditLogRecord> kafkaTemplate =
        org.mockito.Mockito.mock(KafkaTemplate.class);
    KafkaAuditLogStore store = new KafkaAuditLogStore(kafkaTemplate, "audit-topic");
    AuditLogRecord record =
        AuditLogRecord.builder()
            .type("INCOMING")
            .correlationId("cid-123")
            .timestamp(LocalDateTime.now())
            .build();

    store.save(record);

    verify(kafkaTemplate).send("audit-topic", "cid-123", record);
  }
}
