package com.api.audit.storage.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests retention (purging) for the in-memory store.
 *
 * @author Puneet Swarup
 */
class InMemoryAuditLogRetentionTest {

  private AuditLogRecord record(LocalDateTime ts) {
    return AuditLogRecord.builder().type("INCOMING").timestamp(ts).build();
  }

  @Test
  @DisplayName("GIVEN old and new records WHEN purging before a cutoff THEN only old are removed")
  void purgesOnlyOldRecords() {
    InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
    store.save(record(now.minusDays(10)));
    store.save(record(now.minusDays(1)));
    store.save(record(now));

    long deleted = store.purgeBefore(now.minusDays(5));

    assertThat(deleted).isEqualTo(1);
    assertThat(
            store
                .search(com.api.audit.query.AuditLogQuery.builder().size(10).build())
                .getTotalElements())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("GIVEN no old records WHEN purging THEN nothing is removed")
  void purgesNothingWhenNothingOld() {
    InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
    store.save(record(now));

    assertThat(store.purgeBefore(now.minusDays(5))).isZero();
  }
}
