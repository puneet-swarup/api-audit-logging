package com.api.audit.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CleanupConfigTest {

  @TestConfiguration
  @EnableConfigurationProperties(AuditLoggingProperties.class)
  static class TestConfig {}

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CleanupConfig.class))
          .withBean(ApiAuditLogRepository.class, () -> mock(ApiAuditLogRepository.class))
          .withUserConfiguration(TestConfig.class);

  @Test
  @DisplayName("Should not load CleanupConfig when property is disabled")
  void cleanupDisabled() {
    contextRunner
        .withPropertyValues("audit.logging.cleanup.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(CleanupConfig.class));
  }

  @Test
  @DisplayName("Should load CleanupConfig when property is enabled")
  void cleanupEnabled() {
    contextRunner
        .withPropertyValues("audit.logging.cleanup.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(CleanupConfig.class));
  }

  @Test
  @DisplayName("Should calculate correct cutoff date based on retention days")
  void purgeLogicValidation() {
    contextRunner
        .withPropertyValues("audit.logging.cleanup.enabled=true", "audit.logging.cleanup.days=10")
        .run(
            context -> {
              CleanupConfig config = context.getBean(CleanupConfig.class);
              ApiAuditLogRepository repository = context.getBean(ApiAuditLogRepository.class);

              config.purgeOldLogs();

              ArgumentCaptor<LocalDateTime> cutoffCaptor =
                  ArgumentCaptor.forClass(LocalDateTime.class);
              verify(repository).deleteByTimestampBefore(cutoffCaptor.capture());

              LocalDateTime actualCutoff = cutoffCaptor.getValue();
              LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(10);

              assertThat(actualCutoff).isCloseTo(expectedCutoff, within(1, ChronoUnit.MINUTES));
            });
  }

  @Test
  @DisplayName("Should use default 30 days retention when property is not set")
  void purgeLogicDefaultValue() {
    contextRunner
        .withPropertyValues("audit.logging.cleanup.enabled=true")
        .run(
            context -> {
              CleanupConfig config = context.getBean(CleanupConfig.class);
              ApiAuditLogRepository repository = context.getBean(ApiAuditLogRepository.class);

              config.purgeOldLogs();

              ArgumentCaptor<LocalDateTime> cutoffCaptor =
                  ArgumentCaptor.forClass(LocalDateTime.class);
              verify(repository).deleteByTimestampBefore(cutoffCaptor.capture());

              LocalDateTime expectedDefault = LocalDateTime.now().minusDays(30);
              assertThat(cutoffCaptor.getValue())
                  .isCloseTo(expectedDefault, within(1, ChronoUnit.MINUTES));
            });
  }
}
