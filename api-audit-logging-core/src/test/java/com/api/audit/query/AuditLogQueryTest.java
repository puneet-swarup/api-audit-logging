package com.api.audit.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the framework-neutral {@link AuditLogQuery} defaults and clamping.
 *
 * @author Puneet Swarup
 */
class AuditLogQueryTest {

  @Test
  @DisplayName("GIVEN defaults WHEN built THEN safe pagination defaults apply")
  void defaults() {
    AuditLogQuery query = AuditLogQuery.builder().build();
    assertThat(query.effectivePage()).isZero();
    assertThat(query.effectiveSize()).isEqualTo(20);
    assertThat(query.offset()).isZero();
    assertThat(query.getSortBy()).isEqualTo("timestamp");
    assertThat(query.isSortAscending()).isTrue();
  }

  @Test
  @DisplayName("GIVEN a negative page and zero size WHEN resolved THEN they are clamped")
  void clamps() {
    AuditLogQuery query = AuditLogQuery.builder().page(-3).size(0).build();
    assertThat(query.effectivePage()).isZero();
    assertThat(query.effectiveSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("GIVEN page and size WHEN offset computed THEN page*size is returned")
  void offset() {
    AuditLogQuery query = AuditLogQuery.builder().page(2).size(15).build();
    assertThat(query.offset()).isEqualTo(30);
  }

  @Test
  @DisplayName("GIVEN filters WHEN built THEN values are retained")
  void filtersRetained() {
    AuditLogQuery query =
        AuditLogQuery.builder()
            .correlationId("c1")
            .type("INCOMING")
            .tagKey("module")
            .tagValue("payments")
            .httpStatus(500)
            .build();
    assertThat(query.getCorrelationId()).isEqualTo("c1");
    assertThat(query.getType()).isEqualTo("INCOMING");
    assertThat(query.getTagKey()).isEqualTo("module");
    assertThat(query.getTagValue()).isEqualTo("payments");
    assertThat(query.getHttpStatus()).isEqualTo(500);
  }
}
