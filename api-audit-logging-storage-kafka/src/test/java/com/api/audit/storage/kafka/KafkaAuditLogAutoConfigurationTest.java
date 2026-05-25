package com.api.audit.storage.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for Kafka audit sink auto-configuration.
 *
 * <p>Kafka is intentionally opt-in, so these tests cover both sides of that contract: no store is
 * registered by default, and enabling the property creates a store that publishes to the configured
 * topic.
 *
 * @author Puneet Swarup
 */
class KafkaAuditLogAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KafkaAuditLogAutoConfiguration.class))
          .withUserConfiguration(KafkaTemplateConfig.class);

  @Test
  void doesNotRegisterKafkaStoreUntilExplicitlyEnabled() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(AuditLogStore.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void registersKafkaStoreWhenEnabledAndUsesConfiguredTopic() {
    contextRunner
        .withPropertyValues(
            "audit.logging.kafka.enabled=true", "audit.logging.kafka.topic=team-audit-topic")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuditLogStore.class);

              AuditLogRecord record =
                  AuditLogRecord.builder()
                      .type("OUTGOING")
                      .correlationId("corr-kafka-1")
                      .timestamp(LocalDateTime.now())
                      .build();

              context.getBean(AuditLogStore.class).save(record);

              KafkaTemplate<String, AuditLogRecord> kafkaTemplate =
                  context.getBean(KafkaTemplate.class);
              verify(kafkaTemplate).send("team-audit-topic", "corr-kafka-1", record);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class KafkaTemplateConfig {

    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, AuditLogRecord> kafkaTemplate() {
      return org.mockito.Mockito.mock(KafkaTemplate.class);
    }
  }
}
