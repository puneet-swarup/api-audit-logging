package com.api.audit.spi;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link AuditRequest} adapter over a servlet {@link HttpServletRequest}.
 *
 * <p>This is the only place the servlet API meets the policy engine. It is a thin, stateless
 * delegation, so wrapping cost is negligible.
 *
 * @author Puneet Swarup
 */
public class ServletAuditRequest implements AuditRequest {

  private final HttpServletRequest request;

  /**
   * Creates the adapter.
   *
   * @param request the servlet request; never {@code null}
   */
  public ServletAuditRequest(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public String getMethod() {
    return request.getMethod();
  }

  @Override
  public String getPath() {
    return request.getRequestURI();
  }

  @Override
  public String getHeader(String name) {
    return request.getHeader(name);
  }

  @Override
  public Object getAttribute(String name) {
    return request.getAttribute(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    request.setAttribute(name, value);
  }
}
