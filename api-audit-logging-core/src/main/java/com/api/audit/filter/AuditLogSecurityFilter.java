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

    // Only apply security check to the internal audit endpoint
    if (!request.getRequestURI().startsWith(PROTECTED_PATH)) {
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

    if (!configuredKey.equals(providedKey)) {
      log.warn(
          "[AuditLog] Unauthorised request to {} — invalid or missing {} header.",
          PROTECTED_PATH,
          API_KEY_HEADER);
      sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
      return;
    }

    chain.doFilter(request, response);
  }

  private void sendError(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
