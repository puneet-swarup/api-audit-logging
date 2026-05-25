package com.api.audit.storage.kafka;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka implementation of {@link AuditLogStore}.
 *
 * <p>This sink publishes each already-masked audit record to a Kafka topic. It is designed for
 * teams that prefer streaming audit events into a central pipeline, data lake, SIEM, or dedicated
 * search service instead of writing directly to the application's database.
 *
 * <p>The store intentionally does not implement {@code AuditLogSearchStore}; Kafka is a transport,
 * not a query engine. Applications that need the internal search endpoint should pair Kafka with a
 * searchable sink or provide their own search-store implementation.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class KafkaAuditLogStore implements AuditLogStore {

  private final KafkaTemplate<String, AuditLogRecord> kafkaTemplate;
  private final String topic;

  /** Publishes the audit record keyed by correlation ID when available. */
  @Override
  public void save(AuditLogRecord record) {
    kafkaTemplate.send(topic, record.getCorrelationId(), record);
  }
}
