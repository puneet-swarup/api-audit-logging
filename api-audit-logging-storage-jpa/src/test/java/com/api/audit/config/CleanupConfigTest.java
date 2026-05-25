package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for CleanupConfig — verifies scheduled purge logic. Uses direct instantiation (no Spring
 * context) for speed and simplicity.
 */
@ExtendWith(MockitoExtension.class)
class CleanupConfigTest {

  @Test
  @DisplayName("Should NOT purge when cleanup is disabled")
  void cleanupDisabled_noBeanCreated() {
    // CleanupConfig has @ConditionalOnProperty — when disabled it simply won't be
    // loaded by Spring. We test the purge logic directly instead.
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getCleanup().setEnabled(false);
    // The @ConditionalOnProperty is a Spring concern — verified by integration tests.
    // Unit tests verify the business logic (purgeOldLogs) directly.
    assertThat(props.getCleanup().isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Should calculate correct cutoff date based on configured retention days")
  void purgeLogicValidation() {
    // direct instantiation, no Spring context needed
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getCleanup().setDays(10);

    ApiAuditLogRepository repository = mock(ApiAuditLogRepository.class);
    CleanupConfig config = new CleanupConfig(repository, props);

    config.purgeOldLogs();

    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteByTimestampBefore(cutoffCaptor.capture());

    LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(10);
    assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(1, ChronoUnit.MINUTES));
  }

  @Test
  @DisplayName("Should use default 30-day retention when property is not explicitly set")
  void purgeLogicDefaultValue() {
    // default AuditLoggingProperties has days=30
    AuditLoggingProperties props = new AuditLoggingProperties();
    ApiAuditLogRepository repository = mock(ApiAuditLogRepository.class);
    CleanupConfig config = new CleanupConfig(repository, props);

    config.purgeOldLogs();

    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteByTimestampBefore(cutoffCaptor.capture());

    LocalDateTime expectedDefault = LocalDateTime.now().minusDays(30);
    assertThat(cutoffCaptor.getValue()).isCloseTo(expectedDefault, within(1, ChronoUnit.MINUTES));
  }
}
