package com.api.audit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.config.AuditLoggingProperties.PathRule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link PathAuditPolicy}.
 *
 * <p>These tests prove the core promise of Phase 1: a request can be audited purely from
 * configuration, with no annotation and no application code change. They also lock down the
 * specificity and explicit-skip rules that the engine relies on.
 *
 * @author Puneet Swarup
 */
class PathAuditPolicyTest {

  private static AuditLoggingProperties properties(boolean enabled, PathRule... rules) {
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getPolicies().getPathBased().setEnabled(enabled);
    props.getPolicies().getPathBased().setRules(List.of(rules));
    return props;
  }

  private static PathRule rule(String pattern) {
    PathRule r = new PathRule();
    r.setPattern(pattern);
    return r;
  }

  private static MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, path);
    req.setRequestURI(path);
    return req;
  }

  @Test
  @DisplayName("GIVEN path-based disabled WHEN deciding THEN no opinion is returned")
  void disabledPolicyHasNoOpinion() {
    PathAuditPolicy policy = new PathAuditPolicy(properties(false, rule("/api/**")));
    Optional<AuditDecision> decision =
        policy.decide(request("GET", "/api/v1/payments"), "/api/v1/payments");
    assertThat(decision).isEmpty();
  }

  @Test
  @DisplayName(
      "GIVEN matching Ant rule WHEN deciding THEN request is audited without any annotation")
  void matchingAntRuleAuditsRequest() {
    PathRule r = rule("/api/v1/payments/**");
    r.setDescription("Payment APIs");
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, r));

    AuditDecision decision =
        policy
            .decide(request("POST", "/api/v1/payments/submit"), "/api/v1/payments/submit")
            .orElseThrow();

    assertThat(decision.isAudit()).isTrue();
    assertThat(decision.getDescription()).isEqualTo("Payment APIs");
    assertThat(decision.getCaptureMode()).isEqualTo(CaptureMode.FULL);
  }

  @Test
  @DisplayName("GIVEN non-matching path WHEN deciding THEN no opinion is returned")
  void nonMatchingPathHasNoOpinion() {
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, rule("/api/v1/payments/**")));
    assertThat(policy.decide(request("GET", "/api/v1/orders"), "/api/v1/orders")).isEmpty();
  }

  @Test
  @DisplayName("GIVEN rule with methods WHEN method does not match THEN no opinion is returned")
  void methodFilterIsHonored() {
    PathRule r = rule("/api/**");
    r.setMethods(List.of("POST"));
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, r));

    assertThat(policy.decide(request("GET", "/api/v1/x"), "/api/v1/x")).isEmpty();
    assertThat(policy.decide(request("POST", "/api/v1/x"), "/api/v1/x")).isPresent();
  }

  @Test
  @DisplayName("GIVEN explicit skip rule WHEN broader positive rule also matches THEN skip wins")
  void explicitSkipWinsOverPositiveMatch() {
    PathRule positive = rule("/api/**");
    PathRule skip = rule("/api/internal/**");
    skip.setAudit(false);
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, positive, skip));

    AuditDecision decision =
        policy.decide(request("GET", "/api/internal/health"), "/api/internal/health").orElseThrow();

    assertThat(decision.isAudit()).isFalse();
  }

  @Test
  @DisplayName("GIVEN two positive rules WHEN both match THEN the more specific rule wins")
  void moreSpecificRuleWins() {
    PathRule broad = rule("/api/**");
    broad.setDescription("Broad");
    PathRule specific = rule("/api/v1/payments/**");
    specific.setDescription("Specific");
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, broad, specific));

    AuditDecision decision =
        policy
            .decide(request("GET", "/api/v1/payments/status"), "/api/v1/payments/status")
            .orElseThrow();

    assertThat(decision.getDescription()).isEqualTo("Specific");
  }

  @Test
  @DisplayName("GIVEN rule with tags WHEN matched THEN tags flow into the decision")
  void tagsFlowIntoDecision() {
    PathRule r = rule("/api/v1/payments/**");
    r.setTags(java.util.Map.of("module", "payments", "tier", "critical"));
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, r));

    AuditDecision decision =
        policy
            .decide(request("GET", "/api/v1/payments/status"), "/api/v1/payments/status")
            .orElseThrow();

    assertThat(decision.getTags()).containsEntry("module", "payments");
    assertThat(decision.getTags()).containsEntry("tier", "critical");
  }

  @Test
  @DisplayName("GIVEN regex matcher rule WHEN path matches THEN request is audited")
  void regexMatcherWorks() {
    PathRule r = rule("/api/v[0-9]+/orders/.*");
    r.setMatcher(PathMatcherType.REGEX);
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, r));

    assertThat(policy.decide(request("GET", "/api/v2/orders/42"), "/api/v2/orders/42")).isPresent();
    assertThat(policy.decide(request("GET", "/api/orders/42"), "/api/orders/42")).isEmpty();
  }

  @Test
  @DisplayName("GIVEN blank description WHEN matched THEN description falls back to METHOD path")
  void descriptionFallsBackToMethodAndPath() {
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, rule("/api/v1/orders/**")));

    AuditDecision decision =
        policy.decide(request("GET", "/api/v1/orders/7"), "/api/v1/orders/7").orElseThrow();

    assertThat(decision.getDescription()).isEqualTo("GET /api/v1/orders/7");
  }

  @Test
  @DisplayName("GIVEN capture mode configured WHEN matched THEN decision carries the mode")
  void captureModeIsPropagated() {
    PathRule r = rule("/api/**");
    r.setCapture(CaptureMode.METADATA_ONLY);
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, r));

    AuditDecision decision = policy.decide(request("GET", "/api/v1/x"), "/api/v1/x").orElseThrow();
    assertThat(decision.getCaptureMode()).isEqualTo(CaptureMode.METADATA_ONLY);
  }

  @Test
  @DisplayName(
      "GIVEN malformed regex rule WHEN constructed THEN the bad rule is skipped, not fatal")
  void malformedRegexIsSkipped() {
    PathRule bad = rule("([unclosed");
    bad.setMatcher(PathMatcherType.REGEX);
    PathAuditPolicy policy = new PathAuditPolicy(properties(true, bad));

    assertThat(policy.decide(request("GET", "/api/v1/x"), "/api/v1/x")).isEmpty();
  }

  @Test
  @DisplayName(
      "GIVEN specificity WHEN comparing patterns THEN literal and deeper paths score higher")
  void specificityScoring() {
    int broad = PathAuditPolicy.specificity("/api/**", List.of());
    int specific = PathAuditPolicy.specificity("/api/v1/payments/**", List.of());
    int withMethod = PathAuditPolicy.specificity("/api/v1/payments/**", List.of("POST"));

    assertThat(specific).isGreaterThan(broad);
    assertThat(withMethod).isGreaterThan(specific);
  }
}
