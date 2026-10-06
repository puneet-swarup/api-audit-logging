package com.api.audit.policy;

/**
 * Well-known {@link AuditPolicy#getOrder()} values.
 *
 * <p>The engine evaluates policies from the lowest order value to the highest. Explicit-skip
 * semantics are handled by the engine itself (an explicit {@code audit=false} from any policy short
 * circuits the evaluation), so these constants only control the order in which positive decisions
 * are discovered.
 *
 * <p>Custom policies can pick any integer. Use the constants below as reference points:
 *
 * <ul>
 *   <li>{@link #CUSTOM_FIRST} — run before every built-in policy.
 *   <li>{@link #EXPLICIT_SKIP} — where the path policy's explicit skip rules are evaluated.
 *   <li>{@link #PATH_BASED} — path-rule matching.
 *   <li>{@link #ANNOTATION} — the legacy {@code @AuditLog} annotation.
 *   <li>{@link #CUSTOM} — the default for user-supplied policies.
 *   <li>{@link #CUSTOM_LAST} — run after every built-in policy.
 * </ul>
 *
 * @author Puneet Swarup
 * @see AuditPolicy
 */
public final class AuditPolicyOrder {

  /** Run a custom policy before any built-in policy. */
  public static final int CUSTOM_FIRST = -1000;

  /**
   * Order used by the path policy when it matches an explicit {@code audit: false} rule.
   *
   * <p>Explicit skips are evaluated very early so that an operator can always exclude a path even
   * when an annotation or a broader path rule would otherwise include it.
   */
  public static final int EXPLICIT_SKIP = -500;

  /** Order used by the path-based policy for positive rule matches. */
  public static final int PATH_BASED = 100;

  /**
   * Order used by the annotation policy.
   *
   * <p>The annotation runs after path-based positive matches so that, when both match, the
   * annotation (which is the more specific, code-level declaration) is the one whose description
   * and tags are preferred by the engine.
   */
  public static final int ANNOTATION = 200;

  /**
   * Default order for user-supplied policies that do not override {@link AuditPolicy#getOrder()}.
   */
  public static final int CUSTOM = 500;

  /** Run a custom policy after every built-in policy. */
  public static final int CUSTOM_LAST = 1000;

  private AuditPolicyOrder() {
    // Utility holder — not instantiable.
  }
}
