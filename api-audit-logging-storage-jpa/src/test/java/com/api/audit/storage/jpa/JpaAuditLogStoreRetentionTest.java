package com.api.audit.storage.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests retention (purging) for the JPA store.
 *
 * @author Puneet Swarup
 */
@ExtendWith(MockitoExtension.class)
class JpaAuditLogStoreRetentionTest {

  @Mock private ApiAuditLogRepository repository;

  @Test
  @DisplayName(
      "GIVEN a cutoff WHEN purgeBefore THEN the repository delete is invoked and count returned")
  void purgesViaRepository() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
    when(repository.deleteByTimestampBefore(cutoff)).thenReturn(7L);
    JpaAuditLogStore store = new JpaAuditLogStore(repository);

    long deleted = store.purgeBefore(cutoff);

    assertThat(deleted).isEqualTo(7L);
    ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteByTimestampBefore(captor.capture());
    assertThat(captor.getValue()).isEqualTo(cutoff);
  }
}
