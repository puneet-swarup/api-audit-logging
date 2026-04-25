package com.api.audit.feign;

import com.api.audit.annotation.AuditLog;
import com.api.audit.context.CorrelationContext;
import com.api.audit.entity.ApiAuditLog;
import com.api.audit.event.ApiLogEvent;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Custom Feign interceptor and logger that captures outbound HTTP traffic for auditing.
 *
 * <p>Unlike standard SLF4J loggers, this implementation extracts response metadata and payloads to
 * construct an {@link ApiAuditLog}, which is then published as an {@link ApiLogEvent} for
 * asynchronous persistence.
 *
 * <p><b>Key Feature:</b> This class implements "re-buffering." Since Feign's response streams can
 * only be read once, this logger consumes the stream, captures the data, and reconstructs a new
 * {@link Response} object to ensure the calling client can still process the body.
 *
 * @author Puneet Swarup
 */
@AllArgsConstructor
public class CustomFeignLogger extends feign.Logger {

  private final ApplicationEventPublisher publisher;
  private final String appName;

  /**
   * Intercepts the Feign response, captures audit data, and re-buffers the body.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Checks for the presence of {@link AuditLog} on the Feign client method or class.
   *   <li>Buffers the response input stream into a byte array.
   *   <li>Maps Feign request/response metadata to an {@link ApiAuditLog} entity.
   *   <li>Publishes an {@link ApiLogEvent} via the Spring context.
   *   <li>Reconstructs the response body to prevent "Stream Closed" exceptions in the client.
   * </ol>
   *
   * @param configKey the Feign configuration key (typically method signature)
   * @param logLevel the current Feign logging level
   * @param response the intercepted response
   * @param elapsedTime time taken for the request in milliseconds
   * @return a new {@link Response} instance with a buffered body
   * @throws IOException if there is an error reading the response stream
   */
  @Override
  protected Response logAndRebufferResponse(
      String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {

    // Selective logging based on custom annotation presence
    if (!shouldLog(response.request().requestTemplate().methodMetadata().method())) {
      return response;
    }

    byte[] bodyData = Util.toByteArray(response.body().asInputStream());

    ApiAuditLog audit = new ApiAuditLog();
    audit.setServiceName(appName);
    audit.setType("OUTGOING");
    audit.setMethod(response.request().httpMethod().name());
    audit.setUrl(response.request().url());
    audit.setHttpStatus(response.status());
    audit.setDuration(elapsedTime);
    audit.setRequestBody(getCappedBody(response.request().body()));
    audit.setResponseBody(getCappedBody(bodyData));
    audit.setTimestamp(LocalDateTime.now());
    audit.setCorrelationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER));

    publisher.publishEvent(new ApiLogEvent(audit));

    return response.toBuilder().body(bodyData).build();
  }

  /**
   * Overridden to suppress standard Feign console logging. Logic is handled within {@link
   * #logAndRebufferResponse}.
   */
  @Override
  protected void log(String configKey, String format, Object... args) {
    // No-op: Audit logging is event-driven, not console-driven
  }

  /**
   * Determines if the specific Feign method or its parent interface is marked for auditing.
   *
   * @param method the Feign interface method being invoked
   * @return true if {@link AuditLog} is present at the method or class level
   */
  private boolean shouldLog(Method method) {
    return method.isAnnotationPresent(AuditLog.class)
        || method.getDeclaringClass().isAnnotationPresent(AuditLog.class);
  }

  /**
   * Safely converts a byte array to a String, capped at a specific character limit.
   *
   * <p>This prevents excessively large payloads (e.g., file downloads) from exhausting database
   * memory or causing {@code TEXT} column overflows.
   *
   * @param body the raw byte array
   * @return a UTF-8 string representation capped at 20,000 characters
   */
  private String getCappedBody(byte[] body) {
    if (body == null) return "";
    int limit = Math.min(body.length, 20000);
    return new String(body, 0, limit, StandardCharsets.UTF_8);
  }
}
