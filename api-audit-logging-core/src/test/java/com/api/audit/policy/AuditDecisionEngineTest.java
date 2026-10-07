package com.api.audit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.spi.AuditRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuditDecisionEngine}.
 *
 * <p>These tests lock down the precedence contract agreed with the product owner:
 *
 * <ol>
 *   <li>Most-specific / lower-order policy wins for positive decisions.
 *   <li>The annotation beats a generic path match.
 *   <li>An explicit skip always wins, regardless of order.
 *   <li>A failing policy never breaks request handling.
 * </ol>
 *
 * @author Puneet Swarup
 */
class AuditDecisionEngineTest {

  private static AuditPolicy policy(int order, Optional<AuditDecision> result) {
    return new AuditPolicy() {
      @Override
      public int getOrder() {
        return order;
      }

      @Override
      public Optional<AuditDecision> decide(AuditRequest request, String path) {
        return result;
      }
    };
  }

  @Test
  @DisplayName("GIVEN no policies WHEN deciding THEN SKIP is returned")
  void noPoliciesReturnsSkip() {
    AuditDecisionEngine engine = new AuditDecisionEngine(List.of());
    assertThat(engine.decide(null, "/api/v1/x").isAudit()).isFalse();
  }

  @Test
  @DisplayName("GIVEN no policy has an opinion WHEN deciding THEN SKIP is returned")
  void noOpinionReturnsSkip() {
    AuditDecisionEngine engine =
        new AuditDecisionEngine(
            List.of(policy(100, Optional.empty()), policy(200, Optional.empty())));
    assertThat(engine.decide(null, "/api/v1/x").isAudit()).isFalse();
  }

  @Test
  @DisplayName("GIVEN a positive policy WHEN deciding THEN it is audited")
  void positiveDecisionIsAudited() {
    AuditDecisionEngine engine =
        new AuditDecisionEngine(List.of(policy(100, Optional.of(AuditDecision.audit("path")))));
    AuditDecision decision = engine.decide(null, "/api/v1/x");
    assertThat(decision.isAudit()).isTrue();
    assertThat(decision.getDescription()).isEqualTo("path");
  }

  @Test
  @DisplayName("GIVEN annotation and path both match WHEN deciding THEN the annotation wins")
  void annotationBeatsGenericPath() {
    // Path-based has order 100; annotation has order 200 and therefore runs later and overwrites.
    AuditPolicy path = policy(100, Optional.of(AuditDecision.audit("path-description")));
    AuditPolicy annotation =
        policy(200, Optional.of(AuditDecision.audit("annotation-description")));
    AuditDecisionEngine engine = new AuditDecisionEngine(List.of(path, annotation));

    AuditDecision decision = engine.decide(null, "/api/v1/x");
    assertThat(decision.getDescription()).isEqualTo("annotation-description");
  }

  @Test
  @DisplayName("GIVEN an explicit skip from a later policy WHEN deciding THEN skip wins")
  void explicitSkipWinsRegardlessOfOrder() {
    AuditPolicy positive = policy(100, Optional.of(AuditDecision.audit("path")));
    AuditPolicy skip = policy(999, Optional.of(AuditDecision.skip()));
    AuditDecisionEngine engine = new AuditDecisionEngine(List.of(positive, skip));

    assertThat(engine.decide(null, "/api/v1/x").isAudit()).isFalse();
  }

  @Test
  @DisplayName(
      "GIVEN an explicit skip from an earlier policy WHEN deciding THEN evaluation short-circuits")
  void explicitSkipShortCircuits() {
    boolean[] laterPolicyConsulted = {false};
    AuditPolicy skip = policy(100, Optional.of(AuditDecision.skip()));
    AuditPolicy later =
        new AuditPolicy() {
          @Override
          public int getOrder() {
            return 200;
          }

          @Override
          public Optional<AuditDecision> decide(AuditRequest request, String path) {
            laterPolicyConsulted[0] = true;
            return Optional.of(AuditDecision.audit("later"));
          }
        };

    AuditDecisionEngine engine = new AuditDecisionEngine(List.of(skip, later));
    engine.decide(null, "/api/v1/x");

    assertThat(laterPolicyConsulted[0]).isFalse();
  }

  @Test
  @DisplayName("GIVEN a policy that throws WHEN deciding THEN the engine continues safely")
  void failingPolicyIsIsolated() {
    AuditPolicy failing =
        new AuditPolicy() {
          @Override
          public int getOrder() {
            return 100;
          }

          @Override
          public Optional<AuditDecision> decide(AuditRequest request, String path) {
            throw new IllegalStateException("boom");
          }
        };
    AuditPolicy healthy = policy(200, Optional.of(AuditDecision.audit("healthy")));

    AuditDecisionEngine engine = new AuditDecisionEngine(List.of(failing, healthy));
    AuditDecision decision = engine.decide(null, "/api/v1/x");

    assertThat(decision.isAudit()).isTrue();
    assertThat(decision.getDescription()).isEqualTo("healthy");
  }

  @Test
  @DisplayName("GIVEN policies in arbitrary order WHEN constructed THEN they are sorted by order")
  void policiesAreSortedByOrder() {
    AuditPolicy a = policy(500, Optional.empty());
    AuditPolicy b = policy(100, Optional.empty());
    AuditDecisionEngine engine = new AuditDecisionEngine(List.of(a, b));

    assertThat(engine.getPolicies()).containsExactly(b, a);
  }
}
