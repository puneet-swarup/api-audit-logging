package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.policy.AnnotationAuditPolicy;
import com.api.audit.policy.AuditDecisionEngine;
import com.api.audit.policy.PathAuditPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Auto-configuration tests for the Phase 1 policy engine and path-based auditing.
 *
 * <p>These tests assert that the policy beans and the decision engine are registered by default,
 * and that the path-based policy reflects its configuration flag. They are the configuration-level
 * counterpart to {@code PathAuditPolicyTest} (unit) and the demo integration test (end-to-end).
 *
 * @author Puneet Swarup
 */
class PathBasedAuditingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MicrometerAuditMetricsAutoConfiguration.class,
                  AuditMetricsAutoConfiguration.class,
                  LoggingAutoConfiguration.class));

  @Test
  @DisplayName(
      "GIVEN default config WHEN context loads THEN policy engine and policies are present")
  void policyBeansAreRegisteredByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AuditDecisionEngine.class);
          assertThat(context).hasSingleBean(AnnotationAuditPolicy.class);
          assertThat(context).hasSingleBean(PathAuditPolicy.class);
        });
  }

  @Test
  @DisplayName("GIVEN engine WHEN inspected THEN annotation and path policies are both registered")
  void engineContainsBothBuiltInPolicies() {
    contextRunner.run(
        context -> {
          AuditDecisionEngine engine = context.getBean(AuditDecisionEngine.class);
          assertThat(engine.getPolicies())
              .hasAtLeastOneElementOfType(AnnotationAuditPolicy.class)
              .hasAtLeastOneElementOfType(PathAuditPolicy.class);
        });
  }

  @Test
  @DisplayName(
      "GIVEN annotation policy disabled WHEN context loads THEN annotation policy reports no opinion")
  void annotationPolicyCanBeDisabled() {
    contextRunner
        .withPropertyValues("audit.logging.policies.annotation.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuditDecisionEngine.class);
              // The engine still loads; the annotation policy simply returns no opinion.
              assertThat(context).hasSingleBean(AnnotationAuditPolicy.class);
            });
  }

  @Test
  @DisplayName("GIVEN path-based enabled WHEN context loads THEN path policy is active")
  void pathBasedPolicyActivatesFromConfiguration() {
    contextRunner
        .withPropertyValues(
            "audit.logging.policies.path-based.enabled=true",
            "audit.logging.policies.path-based.rules[0].pattern=/api/**",
            "audit.logging.policies.path-based.rules[0].description=API",
            "audit.logging.policies.path-based.rules[0].tags.module=demo")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PathAuditPolicy.class);
              AuditLoggingProperties props = context.getBean(AuditLoggingProperties.class);
              assertThat(props.getPolicies().getPathBased().isEnabled()).isTrue();
              assertThat(props.getPolicies().getPathBased().getRules()).hasSize(1);
              assertThat(props.getPolicies().getPathBased().getRules().get(0).getTags())
                  .containsEntry("module", "demo");
            });
  }
}
