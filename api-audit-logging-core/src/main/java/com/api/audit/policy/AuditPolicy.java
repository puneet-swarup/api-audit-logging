package com.api.audit.policy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Service Provider Interface for deciding whether a request should be audited.
 *
 * <p>The library ships two implementations:
 *
 * <ul>
 *   <li>{@code AnnotationAuditPolicy} — opts a request in when the controller method or class is
 *       annotated with {@code @AuditLog}. This preserves the original behavior of the library.
 *   <li>{@code PathAuditPolicy} — opts a request in when its path matches a rule declared under
 *       {@code audit.logging.policies.path-based.rules}. This is the "add the library and configure
 *       paths, without changing application code" mode.
 * </ul>
 *
 * <p>Applications can supply their own {@code AuditPolicy} bean for advanced routing, for example:
 * auditing based on a header, a tenant, a feature flag, or an environment. Custom policies are
 * resolved together with the built-in ones by {@link AuditDecisionEngine}.
 *
 * <p><b>Return contract:</b> A policy returns an {@link Optional}.
 *
 * <ul>
 *   <li>{@link Optional#empty()} means "I have no opinion about this request". The engine will ask
 *       the next policy.
 *   <li>{@code Optional.of(decision)} means "I have an opinion". A decision with {@link
 *       AuditDecision#isAudit()} {@code == false} acts as an explicit skip and wins over any
 *       positive decision from a lower-priority policy.
 * </ul>
 *
 * <p><b>Thread-safety:</b> Policies are consulted on request threads and must be thread-safe.
 *
 * @author Puneet Swarup
 * @see AuditDecision
 * @see AuditDecisionEngine
 */
public interface AuditPolicy {

  /**
   * Returns the policy's priority. Lower values are consulted first.
   *
   * <p>Built-in priorities are exposed as constants in {@link AuditPolicyOrder}. The engine sorts
   * policies by this value before evaluation, so a custom policy can be placed before or after the
   * built-ins as needed.
   *
   * @return the evaluation order; lower runs earlier
   */
  default int getOrder() {
    return AuditPolicyOrder.CUSTOM;
  }

  /**
   * Evaluates this policy for the given request.
   *
   * @param request the current inbound request; never {@code null}
   * @param path the request path resolved from the request URI; never {@code null}
   * @return an optional decision; empty means the policy has no opinion
   */
  Optional<AuditDecision> decide(HttpServletRequest request, String path);
}
