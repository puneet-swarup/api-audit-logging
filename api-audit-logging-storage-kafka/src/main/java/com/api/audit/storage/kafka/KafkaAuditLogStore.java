package com.api.audit.storage.kafka;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka implementation of {@link AuditLogStore}.
 *
 * <p>Publishes each already-masked audit record to a Kafka topic, keyed by correlation ID. The sink
 * adds production resilience on top of a bare send:
 *
 * <ul>
 *   <li><b>Async failure handling.</b> The producer result is inspected; a failed send is logged.
 *   <li><b>Bounded retries.</b> A failed send is retried up to {@code audit.logging.kafka.retries}
 *       times with a small backoff.
 *   <li><b>Dead-letter topic.</b> If retries are exhausted and a dead-letter topic is configured,
 *       the record is re-published there so it is not lost.
 * </ul>
 *
 * <p>The store never blocks the audit thread and never throws: publish failures are handled
 * asynchronously. Kafka remains a write sink and does not implement search.
 *
 * @author Puneet Swarup
 */
@Slf4j
public class KafkaAuditLogStore implements AuditLogStore {

  private final KafkaTemplate<String, AuditLogRecord> kafkaTemplate;
  private final String topic;
  private final String deadLetterTopic;
  private final int retries;
  private final long retryBackoffMs;

  /**
   * Creates the Kafka sink.
   *
   * @param kafkaTemplate the producer template; never {@code null}
   * @param topic the primary topic; never {@code null}
   * @param deadLetterTopic optional dead-letter topic; may be {@code null} or blank
   * @param retries number of retries after the first attempt; negative values are treated as 0
   * @param retryBackoffMs delay between retries in milliseconds; negative values are treated as 0
   */
  public KafkaAuditLogStore(
      KafkaTemplate<String, AuditLogRecord> kafkaTemplate,
      String topic,
      String deadLetterTopic,
      int retries,
      long retryBackoffMs) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.deadLetterTopic = deadLetterTopic;
    this.retries = Math.max(retries, 0);
    this.retryBackoffMs = Math.max(retryBackoffMs, 0);
  }

  @Override
  public void save(AuditLogRecord record) {
    send(topic, record, 0);
  }

  private void send(String targetTopic, AuditLogRecord record, int attempt) {
    CompletableFuture<?> future =
        kafkaTemplate.send(targetTopic, record.getCorrelationId(), record);
    future.whenComplete(
        (result, ex) -> {
          if (ex == null) {
            return;
          }
          if (attempt < retries) {
            log.warn(
                "[AuditLog] Kafka publish to '{}' failed (attempt {}/{}): {}. Retrying.",
                targetTopic,
                attempt + 1,
                retries + 1,
                ex.getMessage());
            scheduleRetry(targetTopic, record, attempt + 1);
          } else if (deadLetterTopic != null && !deadLetterTopic.isBlank()) {
            routeToDeadLetter(record, ex);
          } else {
            log.error(
                "[AuditLog] Kafka publish to '{}' failed after {} attempt(s) and no dead-letter topic"
                    + " is configured; record for correlationId={} is lost: {}",
                targetTopic,
                retries + 1,
                record.getCorrelationId(),
                ex.getMessage());
          }
        });
  }

  private void scheduleRetry(String targetTopic, AuditLogRecord record, int nextAttempt) {
    if (retryBackoffMs <= 0) {
      send(targetTopic, record, nextAttempt);
      return;
    }
    CompletableFuture.delayedExecutor(retryBackoffMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .execute(() -> send(targetTopic, record, nextAttempt));
  }

  private void routeToDeadLetter(AuditLogRecord record, Throwable cause) {
    log.error(
        "[AuditLog] Kafka publish to '{}' failed after {} attempt(s); routing correlationId={} to"
            + " dead-letter topic '{}': {}",
        topic,
        retries + 1,
        record.getCorrelationId(),
        deadLetterTopic,
        cause.getMessage());
    kafkaTemplate
        .send(deadLetterTopic, record.getCorrelationId(), record)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "[AuditLog] Dead-letter publish to '{}' also failed for correlationId={}: {}",
                    deadLetterTopic,
                    record.getCorrelationId(),
                    ex.getMessage());
              }
            });
  }
}
