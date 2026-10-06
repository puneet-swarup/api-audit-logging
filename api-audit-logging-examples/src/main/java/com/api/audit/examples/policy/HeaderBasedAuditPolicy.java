package com.api.audit.examples.policy;

import com.api.audit.policy.AuditDecision;
import com.api.audit.policy.AuditPolicy;
import com.api.audit.policy.AuditPolicyOrder;
import com.api.audit.policy.CaptureMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * Example: a custom {@link AuditPolicy} driven by a request header.
 *
 * <p>Path and annotation policies cannot express every routing rule. This example audits a request
 * when it carries an {@code X-Audit: on} header, attaching the tenant as a tag. It is registered by
 * {@link CustomPolicyConfiguration} when the {@code policy} profile is active; the engine orders it
 * before the built-ins so it can decide first.
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * curl -H "X-Audit: on" -H "X-Tenant: acme" http://localhost:8080/examples/annotation/hello
 * </pre>
 *
 * <p>Returning {@link Optional#empty()} means "no opinion"; returning a decision with {@code
 * isAudit() == false} is an explicit skip that wins over every other policy.
 *
 * @author Puneet Swarup
 */
public class HeaderBasedAuditPolicy implements AuditPolicy {

  /**
   * Runs before the built-in policies so a header decision is made first.
   *
   * @return the policy order
   */
  @Override
  public int getOrder() {
    return AuditPolicyOrder.CUSTOM_FIRST;
  }

  /**
   * Audits the request when the {@code X-Audit} header equals {@code on}.
   *
   * @param request the current request
   * @param path the request path
   * @return a decision, or empty when the header is absent
   */
  @Override
  public Optional<AuditDecision> decide(HttpServletRequest request, String path) {
    String flag = request.getHeader("X-Audit");
    if (!"on".equalsIgnoreCase(flag)) {
      return Optional.empty();
    }
    String tenant = request.getHeader("X-Tenant");
    Map<String, String> tags = tenant == null ? Map.of() : Map.of("tenant", tenant);
    return Optional.of(new AuditDecision(true, "Header-triggered audit", tags, CaptureMode.FULL));
  }
}
