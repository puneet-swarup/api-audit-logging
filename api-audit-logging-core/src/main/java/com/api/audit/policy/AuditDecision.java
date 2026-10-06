package com.api.audit.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable outcome produced by the audit policy engine for a single request.
 *
 * <p>The engine evaluates one or more {@link AuditPolicy} implementations and reduces their results
 * into exactly one {@code AuditDecision}. The inbound capture filter consults this object once,
 * before it decides whether to allocate request/response wrappers, and then uses it to populate the
 * description, tags, and capture mode of the resulting {@link com.api.audit.model.AuditLogRecord}.
 *
 * <p><b>Single-decision invariant:</b> A request is either audited once or not at all. Because the
 * engine returns a single decision, there is no path by which a request can be published twice,
 * even when both an annotation and a path rule match.
 *
 * <p><b>Immutability:</b> Instances are immutable and safe to share. The {@link #getTags() tags}
 * map is defensively copied on construction and exposed as an unmodifiable view.
 *
 * @author Puneet Swarup
 * @see AuditPolicy
 * @see AuditDecisionEngine
 */
public final class AuditDecision {

  /** Shared "do not audit" decision used when no policy opts the request in. */
  public static final AuditDecision SKIP =
      new AuditDecision(false, null, Collections.emptyMap(), CaptureMode.FULL);

  private final boolean audit;
  private final String description;
  private final Map<String, String> tags;
  private final CaptureMode captureMode;

  /**
   * Creates a decision.
   *
   * @param audit whether the request should be audited
   * @param description human-readable operation label; may be {@code null}
   * @param tags custom key/value dimensions to attach to the record; may be {@code null}
   * @param captureMode how much of the payload to capture; never {@code null}
   */
  public AuditDecision(
      boolean audit, String description, Map<String, String> tags, CaptureMode captureMode) {
    this.audit = audit;
    this.description = description;
    this.tags =
        tags == null || tags.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    this.captureMode = captureMode == null ? CaptureMode.FULL : captureMode;
  }

  /**
   * Convenience factory for an "audit this request" decision with the full capture mode.
   *
   * @param description human-readable operation label; may be {@code null}
   * @return an auditing decision with {@link CaptureMode#FULL}
   */
  public static AuditDecision audit(String description) {
    return new AuditDecision(true, description, Collections.emptyMap(), CaptureMode.FULL);
  }

  /**
   * Convenience factory for a "do not audit" decision.
   *
   * @return the shared {@link #SKIP} decision
   */
  public static AuditDecision skip() {
    return SKIP;
  }

  /**
   * @return {@code true} when the request should be audited
   */
  public boolean isAudit() {
    return audit;
  }

  /**
   * @return the human-readable description, or {@code null} when none was supplied
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return an unmodifiable, never-{@code null} map of custom dimensions
   */
  public Map<String, String> getTags() {
    return tags;
  }

  /**
   * @return the capture mode; never {@code null}
   */
  public CaptureMode getCaptureMode() {
    return captureMode;
  }

  @Override
  public String toString() {
    return "AuditDecision{audit="
        + audit
        + ", description='"
        + description
        + '\''
        + ", tags="
        + tags
        + ", captureMode="
        + captureMode
        + '}';
  }
}
