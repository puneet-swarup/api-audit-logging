package com.api.audit.resttemplate;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.util.AuditMetadataFormatter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * RestTemplate interceptor that captures outbound HTTP exchanges as audit events.
 *
 * <p>The interceptor also propagates the current correlation ID into the outbound request. Response
 * bodies are buffered and wrapped so the calling application can still read them normally after the
 * audit capture has happened.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class AuditRestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;

  /** Captures the request/response pair and publishes it without changing RestTemplate behavior. */
  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String correlationId = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
    if (correlationId != null) {
      request.getHeaders().set(CorrelationContext.CORRELATION_ID_HEADER, correlationId);
    }

    long start = System.currentTimeMillis();
    ClientHttpResponse response;
    try {
      response = execution.execute(request, body);
    } catch (IOException | RuntimeException ex) {
      publishTransportError(request, body, correlationId, start, ex);
      throw ex;
    }
    byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());

    publisher.publishEvent(
        new ApiLogEvent(
            AuditLogRecord.builder()
                .serviceName(appName)
                .type("OUTGOING")
                .method(request.getMethod().name())
                .url(request.getURI().toString())
                .queryString(request.getURI().getRawQuery())
                .requestHeaders(
                    AuditMetadataFormatter.headers(
                        request.getHeaders(), properties.getCapture().getMaxHeaderSize()))
                .responseHeaders(
                    AuditMetadataFormatter.headers(
                        response.getHeaders(), properties.getCapture().getMaxHeaderSize()))
                .requestBody(toBody(body))
                .responseBody(toBody(responseBody))
                .httpStatus(response.getStatusCode().value())
                .duration(System.currentTimeMillis() - start)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build()));

    return new BufferedClientHttpResponse(response, responseBody);
  }

  private void publishTransportError(
      HttpRequest request, byte[] body, String correlationId, long start, Exception ex) {
    publisher.publishEvent(
        new ApiLogEvent(
            AuditLogRecord.builder()
                .serviceName(appName)
                .type("OUTGOING_TRANSPORT_ERROR")
                .method(request.getMethod().name())
                .url(request.getURI().toString())
                .queryString(request.getURI().getRawQuery())
                .requestHeaders(
                    AuditMetadataFormatter.headers(
                        request.getHeaders(), properties.getCapture().getMaxHeaderSize()))
                .requestBody(toBody(body))
                .duration(System.currentTimeMillis() - start)
                .correlationId(correlationId)
                .errorType(ex.getClass().getName())
                .errorMessage(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()));
  }

  private String toBody(byte[] body) {
    if (body == null || body.length == 0) return "";
    int maxBodySize = properties.getCapture().getMaxBodySize();
    if (body.length > maxBodySize) {
      return "[BODY TOO LARGE: " + body.length + " bytes]";
    }
    int limit = Math.min(body.length, maxBodySize);
    return new String(body, 0, limit, StandardCharsets.UTF_8);
  }

  private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body)
      implements ClientHttpResponse {

    @Override
    public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
      return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return delegate.getStatusText();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
      return delegate.getHeaders();
    }
  }
}
