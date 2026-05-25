package com.api.audit.feign;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.util.AuditMetadataFormatter;
import com.api.audit.util.JsonMasker;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Decorating Feign ErrorDecoder that captures outbound failure metadata for auditing.
 *
 * <p>Wraps any existing {@link ErrorDecoder}, audits the failure by publishing an {@link
 * ApiLogEvent}, and delegates exception creation to the wrapped decoder. This preserves custom
 * business exceptions defined in the host application.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class CustomFeignErrorDecoder implements ErrorDecoder {

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final ErrorDecoder delegate;
  private final JsonMasker jsonMasker;
  private final AuditLoggingProperties properties;

  @Override
  public Exception decode(String methodKey, Response response) {
    try {
      byte[] bodyData =
          response.body() == null ? new byte[0] : Util.toByteArray(response.body().asInputStream());
      String rawBody = new String(bodyData, StandardCharsets.UTF_8);

      AuditLogRecord record =
          AuditLogRecord.builder()
              .serviceName(appName)
              .type("OUTGOING_ERROR")
              .method(response.request().httpMethod().name())
              .url(response.request().url())
              .requestHeaders(
                  AuditMetadataFormatter.headers(
                      response.request().headers(), properties.getCapture().getMaxHeaderSize()))
              .responseHeaders(
                  AuditMetadataFormatter.headers(
                      response.headers(), properties.getCapture().getMaxHeaderSize()))
              .httpStatus(response.status())
              .errorType("HTTP_" + response.status())
              .errorMessage(response.reason())
              .timestamp(LocalDateTime.now())
              .correlationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER))
              .responseBody(jsonMasker.mask(cappedBody(rawBody, bodyData.length)))
              .build();

      if (response.body() != null) {
        response = response.toBuilder().body(bodyData).build();
      }
      publisher.publishEvent(new ApiLogEvent(record));
    } catch (IOException ignored) {
      // Never block error propagation due to audit failure
    }

    return delegate.decode(methodKey, response);
  }

  private String cappedBody(String rawBody, int byteLength) {
    return byteLength > properties.getCapture().getMaxBodySize()
        ? "[BODY TOO LARGE: " + byteLength + " bytes]"
        : rawBody;
  }
}
