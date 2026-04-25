package com.api.audit.interceptor;

import com.api.audit.annotation.AuditLog;
import com.api.audit.filter.IncomingLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that determines if a web request should be audited based on annotations.
 *
 * <p>This interceptor scans the target controller method and class for the {@link AuditLog}
 * annotation. If present, it flags the request for auditing by setting specific request attributes.
 * These attributes are subsequently consumed by the {@code IncomingLoggingFilter} during the
 * response finalization phase.
 *
 * <p><b>Execution Order:</b> This interceptor runs after the filter chain has started but before
 * the controller method is executed. It acts as a metadata provider for the logging infrastructure.
 *
 * @author Puneet Swarup
 * @see AuditLog
 * @see IncomingLoggingFilter
 */
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

  private final ApplicationEventPublisher publisher;

  /**
   * Intercepts the request execution to check for auditing requirements.
   *
   * <p>The logic follows a hierarchical lookup for the {@link AuditLog} annotation:
   *
   * <ol>
   *   <li>Checks the specific handler method being invoked.
   *   <li>If not found on the method, checks the controller class (Bean type).
   * </ol>
   *
   * <p>If an annotation is found, the following request attributes are set:
   *
   * <ul>
   *   <li>{@code AUDIT_LOG_ENABLED}: A boolean flag to signal the logging filter.
   *   <li>{@code AUDIT_LOG_DESC}: The descriptive text provided in the annotation's value.
   * </ul>
   *
   * @param request current HTTP request
   * @param response current HTTP response
   * @param handler the chosen handler to execute, for type and/or instance evaluation
   * @return {@code true} always, to ensure the request execution chain is never blocked
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    if (handler instanceof HandlerMethod handlerMethod) {
      // Priority 1: Method-level annotation
      AuditLog annotation = handlerMethod.getMethodAnnotation(AuditLog.class);

      // Priority 2: Class-level annotation
      if (annotation == null) {
        annotation = handlerMethod.getBeanType().getAnnotation(AuditLog.class);
      }

      if (annotation != null) {
        request.setAttribute("AUDIT_LOG_ENABLED", true);
        request.setAttribute("AUDIT_LOG_DESC", annotation.value());
      }
    }

    return true;
  }
}
