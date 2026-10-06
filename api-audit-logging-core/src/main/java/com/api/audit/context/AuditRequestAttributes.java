package com.api.audit.context;

/**
 * Central holder for the {@link jakarta.servlet.http.HttpServletRequest} attribute names used to
 * carry audit metadata between capture components.
 *
 * <p>Before this class existed, the attribute names {@code "AUDIT_LOG_ENABLED"} and {@code
 * "AUDIT_LOG_DESC"} were duplicated as string literals inside {@link
 * com.api.audit.interceptor.AuditLogInterceptor} and {@link
 * com.api.audit.filter.IncomingLoggingFilter}. Any typo in one of those literals would silently
 * break the annotation-based flow because the write and the read would target different keys.
 *
 * <p>By funnelling every read and write through these constants, the compiler now guards against
 * that class of bug and the contract is documented in a single place.
 *
 * <p><b>Attribute lifecycle:</b> Both attributes are request-scoped. They are set by the annotation
 * interceptor during {@code preHandle} and consumed by the inbound filter in its {@code finally}
 * block after the response has been produced.
 *
 * @author Puneet Swarup
 * @see com.api.audit.interceptor.AuditLogInterceptor
 * @see com.api.audit.filter.IncomingLoggingFilter
 */
public final class AuditRequestAttributes {

  /**
   * Boolean attribute ({@code Boolean.TRUE}) that marks a request as eligible for auditing when the
   * annotation-based policy is in use.
   *
   * <p>The inbound filter checks for the presence of this attribute before publishing an audit
   * event. With the path-based policy engine in place, the filter may also publish based on a path
   * rule, but the annotation interceptor continues to set this attribute so the two mechanisms
   * remain interoperable and backward compatible.
   */
  public static final String AUDIT_LOG_ENABLED = "AUDIT_LOG_ENABLED";

  /**
   * String attribute holding the human-readable description declared on the {@link
   * com.api.audit.annotation.AuditLog} annotation.
   *
   * <p>When present, the inbound filter copies this value into the {@code description} field of the
   * audit record. When the path-based policy engine supplies its own description, the engine value
   * is preferred and this attribute is left untouched.
   */
  public static final String AUDIT_LOG_DESC = "AUDIT_LOG_DESC";

  private AuditRequestAttributes() {
    // Utility holder — not instantiable.
  }
}
