package com.api.audit.storage.kafka;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration for publishing audit records to Kafka.
 *
 * <p>Kafka is intentionally opt-in. Add the module and set {@code audit.logging.kafka.enabled=true}
 * once the topic, retention, and downstream processing path are ready.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(KafkaTemplate.class)
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnProperty(
    prefix = "audit.logging.kafka",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class KafkaAuditLogAutoConfiguration {

  /** Registers the Kafka sink when the application has not provided another audit store. */
  @Bean
  @ConditionalOnMissingBean(AuditLogStore.class)
  public AuditLogStore kafkaAuditLogStore(
      KafkaTemplate<String, AuditLogRecord> kafkaTemplate, AuditLoggingProperties properties) {
    return new KafkaAuditLogStore(kafkaTemplate, properties.getKafka().getTopic());
  }
}
