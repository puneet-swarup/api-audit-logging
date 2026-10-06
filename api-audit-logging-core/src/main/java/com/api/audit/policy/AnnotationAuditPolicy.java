package com.api.audit.policy;

import com.api.audit.context.AuditRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Policy that opts a request into auditing when the target handler was annotated with {@link
 * com.api.audit.annotation.AuditLog}.
 *
 * <p>The annotation itself is resolved by {@link com.api.audit.interceptor.AuditLogInterceptor}
 * during {@code preHandle}, because that is the first point in the request lifecycle where the
 * chosen {@code HandlerMethod} is known. The interceptor then records its finding on the request as
 * {@link AuditRequestAttributes#AUDIT_LOG_ENABLED} and {@link
 * AuditRequestAttributes#AUDIT_LOG_DESC}. This policy simply translates that request-scoped flag
 * into an {@link AuditDecision}.
 *
 * <p>Keeping the resolution in the interceptor and the decision in a policy preserves the original
 * capture mechanics (minimal change, minimal risk) while allowing annotations to coexist with
 * path-based rules and custom policies inside one engine.
 *
 * <p>This policy is active by default and can be disabled with {@code
 * audit.logging.policies.annotation.enabled=false}.
 *
 * @author Puneet Swarup
 * @see AuditPolicy
 * @see AuditDecision
 */
public class AnnotationAuditPolicy implements AuditPolicy {

  private final boolean enabled;

  /**
   * Creates the policy.
   *
   * @param enabled when {@code false}, the policy always returns {@link Optional#empty()} and the
   *     annotation-based flow is effectively turned off
   */
  public AnnotationAuditPolicy(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int getOrder() {
    return AuditPolicyOrder.ANNOTATION;
  }

  @Override
  public Optional<AuditDecision> decide(HttpServletRequest request, String path) {
    if (!enabled) {
      return Optional.empty();
    }

    Object enabledFlag = request.getAttribute(AuditRequestAttributes.AUDIT_LOG_ENABLED);
    if (!Boolean.TRUE.equals(enabledFlag)) {
      return Optional.empty();
    }

    Object description = request.getAttribute(AuditRequestAttributes.AUDIT_LOG_DESC);
    String value = description == null ? null : description.toString();
    return Optional.of(new AuditDecision(true, value, null, CaptureMode.FULL));
  }
}
