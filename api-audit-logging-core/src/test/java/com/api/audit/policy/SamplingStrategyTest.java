package com.api.audit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SamplingStrategy}.
 *
 * <p>These tests prove the two guarantees operators rely on: a rate of 1.0 (or disabled sampling)
 * never drops a record, and error records are always kept even when sampling is aggressive.
 *
 * @author Puneet Swarup
 */
class SamplingStrategyTest {

  private static AuditLogRecord record(String type, Integer status) {
    return AuditLogRecord.builder().type(type).httpStatus(status).build();
  }

  @Test
  @DisplayName("GIVEN sampling disabled WHEN shouldStore THEN every record is kept")
  void disabledKeepsEverything() {
    SamplingStrategy strategy = new SamplingStrategy(false, 0.0, true);
    assertThat(strategy.shouldStore(record("INCOMING", 200))).isTrue();
    assertThat(strategy.shouldStore(record("INCOMING", 200))).isTrue();
    assertThat(strategy.isActive()).isFalse();
  }

  @Test
  @DisplayName("GIVEN sample rate 1.0 WHEN shouldStore THEN every record is kept")
  void fullRateKeepsEverything() {
    SamplingStrategy strategy = new SamplingStrategy(true, 1.0, true);
    assertThat(strategy.shouldStore(record("INCOMING", 200))).isTrue();
    assertThat(strategy.isActive()).isFalse();
  }

  @Test
  @DisplayName("GIVEN rate 0.0 WHEN shouldStore a success THEN it is dropped")
  void zeroRateDropsSuccess() {
    SamplingStrategy strategy = new SamplingStrategy(true, 0.0, true);
    assertThat(strategy.shouldStore(record("INCOMING", 200))).isFalse();
    assertThat(strategy.getDroppedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "GIVEN always-capture-errors WHEN an error record arrives THEN it is kept at rate 0.0")
  void errorsAreAlwaysKept() {
    SamplingStrategy strategy = new SamplingStrategy(true, 0.0, true);
    assertThat(strategy.shouldStore(record("INCOMING_ERROR", 500))).isTrue();
    assertThat(strategy.shouldStore(record("INCOMING", 503))).isTrue();
    assertThat(strategy.getKeptCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "GIVEN always-capture-errors disabled WHEN an error arrives THEN normal sampling applies")
  void errorsSampledWhenFlagDisabled() {
    SamplingStrategy strategy = new SamplingStrategy(true, 0.0, false);
    assertThat(strategy.shouldStore(record("INCOMING_ERROR", 500))).isFalse();
  }

  @Test
  @DisplayName("GIVEN rate 0.5 WHEN many records are evaluated THEN roughly half are kept")
  void approximateRateIsHonored() {
    SamplingStrategy strategy = new SamplingStrategy(true, 0.5, false);
    int kept = 0;
    int total = 10_000;
    for (int i = 0; i < total; i++) {
      if (strategy.shouldStore(record("INCOMING", 200))) {
        kept++;
      }
    }
    assertThat(kept).isBetween((int) (total * 0.40), (int) (total * 0.60));
    assertThat(strategy.getKeptCount() + strategy.getDroppedCount()).isEqualTo(total);
  }

  @Test
  @DisplayName("GIVEN out-of-range rate WHEN constructed THEN it is clamped")
  void rateIsClamped() {
    assertThat(new SamplingStrategy(true, 2.0, true).isActive()).isFalse();
    assertThat(new SamplingStrategy(true, -1.0, true).isActive()).isTrue();
  }
}
