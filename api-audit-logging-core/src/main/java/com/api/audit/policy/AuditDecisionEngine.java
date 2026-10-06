package com.api.audit.policy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Single decision point that combines every registered {@link AuditPolicy} into one {@link
 * AuditDecision} per request.
 *
 * <p>The engine replaces the ad-hoc gating that previously lived inside {@link
 * com.api.audit.filter.IncomingLoggingFilter} and {@link
 * com.api.audit.interceptor.AuditLogInterceptor}. It is the reason annotation-based and path-based
 * auditing can coexist without double-publishing a record.
 *
 * <p><b>Resolution algorithm:</b>
 *
 * <ol>
 *   <li>Policies are sorted by {@link AuditPolicy#getOrder()} ascending (lower runs first).
 *   <li>Each policy is consulted in turn.
 *   <li>A policy returning {@link Optional#empty()} is skipped (no opinion).
 *   <li>A policy returning an explicit skip ({@code isAudit() == false}) immediately wins and the
 *       engine stops evaluating further policies.
 *   <li>A policy returning a positive decision is recorded as the current best but evaluation
 *       continues, so a later policy with a more specific view can still supply a skip.
 *   <li>If at least one positive decision was seen, the last recorded positive decision is returned
 *       (which, given the built-in ordering, means the annotation wins over a generic path match).
 *   <li>If no policy had an opinion, {@link AuditDecision#SKIP} is returned.
 * </ol>
 *
 * <p><b>Single-publish invariant:</b> the engine returns exactly one decision, so the capture
 * filter can publish at most one audit record per request.
 *
 * <p><b>Thread safety:</b> the policy list is copied and sorted once in the constructor and never
 * mutated. The engine is safe for concurrent use.
 *
 * @author Puneet Swarup
 * @see AuditPolicy
 * @see AuditDecision
 */
@Slf4j
public class AuditDecisionEngine {

  private final List<AuditPolicy> policies;

  /**
   * Creates the engine.
   *
   * @param policies the policies to evaluate; may be empty; never {@code null}
   */
  public AuditDecisionEngine(List<AuditPolicy> policies) {
    List<AuditPolicy> ordered = new ArrayList<>(policies == null ? List.of() : policies);
    ordered.sort(Comparator.comparingInt(AuditPolicy::getOrder));
    this.policies = List.copyOf(ordered);
    log.info(
        "[AuditLog] Audit decision engine initialised with {} policy(ies): {}",
        this.policies.size(),
        this.policies.stream().map(p -> p.getClass().getSimpleName()).toList());
  }

  /**
   * Resolves the audit decision for a request.
   *
   * @param request the current inbound request; never {@code null}
   * @param path the request path to match against path rules; never {@code null}
   * @return the resolved decision; never {@code null}, defaults to {@link AuditDecision#SKIP}
   */
  public AuditDecision decide(HttpServletRequest request, String path) {
    AuditDecision best = null;
    for (AuditPolicy policy : policies) {
      Optional<AuditDecision> result;
      try {
        result = policy.decide(request, path);
      } catch (RuntimeException ex) {
        // A faulty policy must never break request handling.
        log.error(
            "[AuditLog] Audit policy {} failed for path '{}': {}",
            policy.getClass().getSimpleName(),
            path,
            ex.getMessage(),
            ex);
        continue;
      }

      if (result.isEmpty()) {
        continue;
      }

      AuditDecision decision = result.get();
      if (!decision.isAudit()) {
        // Explicit skip wins immediately and is final.
        return decision;
      }
      best = decision;
    }
    return best == null ? AuditDecision.SKIP : best;
  }

  /**
   * @return the ordered, immutable policy list backing this engine (for diagnostics/tests)
   */
  public List<AuditPolicy> getPolicies() {
    return policies;
  }
}
