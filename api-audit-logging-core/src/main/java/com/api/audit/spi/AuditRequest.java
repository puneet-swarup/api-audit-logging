package com.api.audit.spi;

/**
 * Framework-neutral view of an inbound request, used by the audit policy engine.
 *
 * <p>Policies previously depended on {@code jakarta.servlet.http.HttpServletRequest}, which forced
 * a servlet dependency onto any capture source. This abstraction exposes only what a policy needs —
 * path, method, headers, and mutable attributes — so the same policies work for servlet MVC and
 * reactive WebFlux through thin adapters.
 *
 * <p><b>Attributes</b> are request-scoped key/value pairs. The annotation interceptor uses them to
 * record that a handler was annotated; a reactive adapter can do the same.
 *
 * @author Puneet Swarup
 * @see com.api.audit.policy.AuditPolicy
 */
public interface AuditRequest {

  /**
   * Returns the request method (GET, POST, ...).
   *
   * @return the HTTP method; never {@code null}
   */
  String getMethod();

  /**
   * Returns the request path (URI without query string).
   *
   * @return the path; never {@code null}
   */
  String getPath();

  /**
   * Returns a header value.
   *
   * @param name the header name
   * @return the header value, or {@code null} when absent
   */
  String getHeader(String name);

  /**
   * Returns a request attribute.
   *
   * @param name the attribute name
   * @return the attribute value, or {@code null} when absent
   */
  Object getAttribute(String name);

  /**
   * Sets a request attribute.
   *
   * @param name the attribute name
   * @param value the attribute value
   */
  void setAttribute(String name, Object value);
}
