package com.api.audit.storage.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the Kafka audit sink against a real broker.
 *
 * <p>Produces an {@link AuditLogRecord} through {@link KafkaAuditLogStore} and consumes it back,
 * asserting the key is the correlation ID and the payload round-trips. Skipped automatically when
 * no Docker runtime is available.
 *
 * @author Puneet Swarup
 */
@EnabledIf("dockerAvailable")
class KafkaAuditLogStoreIT {

  /** Condition method used by {@link EnabledIf} to skip when Docker is unavailable. */
  static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  void publishesRecordKeyedByCorrelationId() {
    try (KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))) {
      kafka.start();
      String topic = "api-audit-logs-it";

      KafkaTemplate<String, AuditLogRecord> template = template(kafka.getBootstrapServers());
      KafkaAuditLogStore store = new KafkaAuditLogStore(template, topic, null, 0, 0);

      AuditLogRecord record =
          AuditLogRecord.builder()
              .serviceName("payments")
              .type("INCOMING")
              .method("POST")
              .url("/api/v1/payments")
              .correlationId("corr-it-1")
              .timestamp(LocalDateTime.now())
              .build();

      store.save(record);
      template.flush();

      try (Consumer<String, String> consumer = consumer(kafka.getBootstrapServers())) {
        consumer.subscribe(java.util.List.of(topic));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(20));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> first = records.iterator().next();
        assertThat(first.key()).isEqualTo("corr-it-1");
        assertThat(first.value()).contains("payments").contains("/api/v1/payments");
      }
    }
  }

  private KafkaTemplate<String, AuditLogRecord> template(String bootstrap) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  private Consumer<String, String> consumer(String bootstrap) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-it");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
  }
}
