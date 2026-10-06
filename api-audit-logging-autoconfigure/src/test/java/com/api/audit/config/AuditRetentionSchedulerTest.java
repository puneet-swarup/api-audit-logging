package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.spi.AuditRetentionPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditRetentionScheduler}.
 *
 * @author Puneet Swarup
 */
class AuditRetentionSchedulerTest {

  private static final class RecordingPolicy implements AuditRetentionPolicy {
    LocalDateTime cutoff;
    long returnValue;
    boolean throwOnPurge;

    @Override
    public long purgeBefore(LocalDateTime cutoff) {
      this.cutoff = cutoff;
      if (throwOnPurge) {
        throw new IllegalStateException("boom");
      }
      return returnValue;
    }
  }

  @Test
  @DisplayName("GIVEN a policy WHEN purgeExpired runs THEN it is invoked with the retention cutoff")
  void invokesPolicies() {
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getCleanup().setDays(30);
    RecordingPolicy policy = new RecordingPolicy();
    policy.returnValue = 5;

    AuditRetentionScheduler scheduler = new AuditRetentionScheduler(List.of(policy), props);
    scheduler.purgeExpired();

    assertThat(policy.cutoff).isNotNull();
    // The cutoff should be ~30 days in the past.
    assertThat(policy.cutoff).isBefore(LocalDateTime.now().minusDays(29));
  }

  @Test
  @DisplayName("GIVEN no policies WHEN purgeExpired runs THEN nothing happens")
  void handlesNoPolicies() {
    AuditRetentionScheduler scheduler =
        new AuditRetentionScheduler(List.of(), new AuditLoggingProperties());
    scheduler.purgeExpired();
  }

  @Test
  @DisplayName("GIVEN a failing policy WHEN another succeeds THEN the failure is isolated")
  void isolatesFailures() {
    AuditLoggingProperties props = new AuditLoggingProperties();
    RecordingPolicy failing = new RecordingPolicy();
    failing.throwOnPurge = true;
    RecordingPolicy healthy = new RecordingPolicy();
    healthy.returnValue = 3;

    List<AuditRetentionPolicy> policies = new ArrayList<>();
    policies.add(failing);
    policies.add(healthy);

    AuditRetentionScheduler scheduler = new AuditRetentionScheduler(policies, props);
    scheduler.purgeExpired();

    // The healthy policy was still invoked despite the failing one.
    assertThat(healthy.cutoff).isNotNull();
  }
}
