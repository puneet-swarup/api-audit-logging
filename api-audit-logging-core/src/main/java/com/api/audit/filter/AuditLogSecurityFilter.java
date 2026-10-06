package com.api.audit.filter;

import com.api.audit.config.AuditLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security filter that protects the {@code /internal/audit-logs} management endpoint.
 *
 * <p>Every request to {@code /internal/audit-logs} must include a shared API key in the {@value
 * #API_KEY_HEADER} request header. The expected key is configured via {@code
 * audit.logging.internal.api-key}.
 *
 * <p><b>Fail-secure behaviour:</b> If no key is configured (the property is blank or absent), the
 * endpoint is blocked entirely with {@code 403 Forbidden}. This prevents the endpoint from being
 * accidentally open in environments where the property was forgotten.
 *
 * <p>All other request paths pass through this filter without any checks.
 *
 * @author Puneet Swarup
 */
@Slf4j
@RequiredArgsConstructor
public class AuditLogSecurityFilter extends OncePerRequestFilter {

  /** The HTTP request header that must carry the configured API key. */
  public static final String API_KEY_HEADER = "X-Audit-Api-Key";

  private static final String PROTECTED_PATH = "/internal/audit-logs";

  private final AuditLoggingProperties properties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    // Only apply the security check to the internal audit endpoint. The match must be exact or a
    // child path (e.g. /internal/audit-logs/42), never a prefix, so a lookalike path such as
    // /internal/audit-logs-evil is not accidentally treated as protected.
    if (!isProtectedPath(request.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }

    String configuredKey = properties.getInternal().getApiKey();

    // Fail-secure: if no key is configured, block the endpoint entirely
    if (!StringUtils.hasText(configuredKey)) {
      log.warn(
          "[AuditLog] Request to {} blocked — audit.logging.internal.api-key is not configured.",
          PROTECTED_PATH);
      sendError(response, HttpStatus.FORBIDDEN, "Audit endpoint is not configured");
      return;
    }

    String providedKey = request.getHeader(API_KEY_HEADER);

    if (!constantTimeEquals(configuredKey, providedKey)) {
      log.warn(
          "[AuditLog] Unauthorised request to {} — invalid or missing {} header.",
          PROTECTED_PATH,
          API_KEY_HEADER);
      sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
      return;
    }

    chain.doFilter(request, response);
  }

  /**
   * Returns whether the request path targets the protected endpoint. A path matches when it equals
   * the protected path exactly or is a child of it, but never when it merely shares a prefix (so
   * {@code /internal/audit-logs-evil} is not protected).
   *
   * @param path the request URI path
   * @return {@code true} when the path is protected
   */
  private boolean isProtectedPath(String path) {
    if (path == null) {
      return false;
    }
    return path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/");
  }

  /**
   * Compares two strings in a way that does not short-circuit on the first differing character, to
   * avoid leaking information about the configured key through response timing.
   *
   * @param expected the configured key; never {@code null}
   * @param provided the value supplied by the caller; may be {@code null}
   * @return {@code true} when both values are equal
   */
  private boolean constantTimeEquals(String expected, String provided) {
    if (provided == null) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void sendError(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
