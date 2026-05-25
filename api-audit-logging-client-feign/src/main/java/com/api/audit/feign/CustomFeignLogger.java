package com.api.audit.feign;

import com.api.audit.annotation.AuditLog;
import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.util.AuditMetadataFormatter;
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
 * Custom Feign Logger that captures outbound HTTP calls for auditing.
 *
 * <p>Intercepts Feign responses, builds an {@link AuditLogRecord}, publishes it as an {@link
 * ApiLogEvent}, and re-buffers the response body so the calling Feign client can still read it.
 * Only captures calls annotated with {@link AuditLog}.
 *
 * @author Puneet Swarup
 */
@AllArgsConstructor
public class CustomFeignLogger extends feign.Logger {

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;

  @Override
  protected Response logAndRebufferResponse(
      String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {

    if (!shouldLog(response.request().requestTemplate().methodMetadata().method())) {
      return response;
    }

    byte[] bodyData =
        response.body() == null ? new byte[0] : Util.toByteArray(response.body().asInputStream());

    AuditLogRecord record =
        AuditLogRecord.builder()
            .serviceName(appName)
            .type("OUTGOING")
            .method(response.request().httpMethod().name())
            .url(response.request().url())
            .requestHeaders(
                AuditMetadataFormatter.headers(
                    response.request().headers(), properties.getCapture().getMaxHeaderSize()))
            .responseHeaders(
                AuditMetadataFormatter.headers(
                    response.headers(), properties.getCapture().getMaxHeaderSize()))
            .httpStatus(response.status())
            .duration(elapsedTime)
            .requestBody(getCappedBody(response.request().body()))
            .responseBody(getCappedBody(bodyData))
            .timestamp(LocalDateTime.now())
            .correlationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER))
            .build();

    publisher.publishEvent(new ApiLogEvent(record));

    return response.body() == null ? response : response.toBuilder().body(bodyData).build();
  }

  @Override
  protected void log(String configKey, String format, Object... args) {
    // No-op: audit logging is event-driven, not console-driven
  }

  private boolean shouldLog(Method method) {
    return method.isAnnotationPresent(AuditLog.class)
        || method.getDeclaringClass().isAnnotationPresent(AuditLog.class);
  }

  private String getCappedBody(byte[] body) {
    if (body == null) return "";
    int maxBodySize = properties.getCapture().getMaxBodySize();
    if (body.length > maxBodySize) {
      return "[BODY TOO LARGE: " + body.length + " bytes]";
    }
    int limit = Math.min(body.length, maxBodySize);
    return new String(body, 0, limit, StandardCharsets.UTF_8);
  }
}
