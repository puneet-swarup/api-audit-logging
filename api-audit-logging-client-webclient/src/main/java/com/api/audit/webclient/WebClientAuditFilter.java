package com.api.audit.webclient;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.util.AuditMetadataFormatter;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Builds the {@link ExchangeFilterFunction} that captures WebClient exchanges as audit records.
 *
 * <p>Metadata (method, URL, status, timing, headers, correlation ID) is always captured. When body
 * capture is enabled, the <b>response</b> body is buffered and re-published so downstream
 * subscribers still receive it. Response-body capture is safe and self-contained: the body is
 * drained into memory, captured, and rebuilt on the response.
 *
 * <p><b>Request bodies</b> are not captured by the filter, because a {@code ClientRequest} exposes
 * its body only as an opaque {@code BodyInserter} that cannot be introspected without changing how
 * the request is built. To capture a request body, build the request with {@link
 * AuditBodyInserters}, which records the body and still writes it to the network. See that class
 * for the pattern.
 *
 * <p><b>Design.</b> Body buffering primitives live in {@link WebClientBodyCapture}; this class
 * assembles the WebClient-specific record. One responsibility per class.
 *
 * @author Puneet Swarup
 * @see AuditBodyInserters
 */
public class WebClientAuditFilter {

  private static final String NOT_CAPTURED = "[WEBCLIENT REQUEST BODY NOT CAPTURED]";

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;
  private final boolean captureBodies;
  private final WebClientBodyCapture bodyCapture;

  /**
   * Creates the filter factory.
   *
   * @param publisher publishes audit events
   * @param appName the service name recorded on every audit record
   * @param properties the library configuration properties
   */
  public WebClientAuditFilter(
      ApplicationEventPublisher publisher, String appName, AuditLoggingProperties properties) {
    this.publisher = publisher;
    this.appName = appName;
    this.properties = properties;
    this.captureBodies = properties.getWebclient().isCaptureBodies();
    this.bodyCapture = new WebClientBodyCapture(properties.getCapture().getMaxBodySize());
  }

  /**
   * Builds the exchange filter function.
   *
   * @return the filter to register on a {@code WebClient.Builder}
   */
  public ExchangeFilterFunction filter() {
    return (request, next) -> {
      String correlationId = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
      ClientRequest requestToUse =
          correlationId == null
              ? request
              : ClientRequest.from(request)
                  .header(CorrelationContext.CORRELATION_ID_HEADER, correlationId)
                  .build();

      long start = System.currentTimeMillis();
      return next.exchange(requestToUse)
          .doOnError(error -> publishTransportError(requestToUse, correlationId, start, error))
          .flatMap(
              response -> {
                if (!captureBodies) {
                  publishSuccess(requestToUse, correlationId, start, response, NOT_CAPTURED);
                  return Mono.just(response);
                }
                return bufferAndPublish(requestToUse, correlationId, start, response);
              });
    };
  }

  private Mono<ClientResponse> bufferAndPublish(
      ClientRequest request, String correlationId, long start, ClientResponse response) {
    return response
        .bodyToFlux(DataBuffer.class)
        .collectList()
        .defaultIfEmpty(List.of())
        .flatMap(
            buffers -> {
              byte[] bytes = WebClientBodyCapture.join(buffers);
              String responseBody = bodyCapture.toCapturedString(bytes);

              ClientResponse rebuilt =
                  ClientResponse.from(response)
                      .body(Flux.defer(() -> Flux.just(wrap(bytes))))
                      .build();

              publishSuccess(request, correlationId, start, rebuilt, responseBody);
              return Mono.just(rebuilt);
            });
  }

  private DataBuffer wrap(byte[] bytes) {
    return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
  }

  private void publishSuccess(
      ClientRequest request,
      String correlationId,
      long start,
      ClientResponse response,
      String responseBody) {
    publisher.publishEvent(
        new ApiLogEvent(
            AuditLogRecord.builder()
                .serviceName(appName)
                .type("OUTGOING")
                .method(request.method().name())
                .url(request.url().toString())
                .queryString(request.url().getRawQuery())
                .requestHeaders(
                    AuditMetadataFormatter.headers(
                        request.headers(), properties.getCapture().getMaxHeaderSize()))
                .responseHeaders(
                    AuditMetadataFormatter.headers(
                        response.headers().asHttpHeaders(),
                        properties.getCapture().getMaxHeaderSize()))
                .requestBody(NOT_CAPTURED)
                .responseBody(responseBody)
                .httpStatus(response.statusCode().value())
                .duration(System.currentTimeMillis() - start)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build()));
  }

  private void publishTransportError(
      ClientRequest request, String correlationId, long start, Throwable error) {
    publisher.publishEvent(
        new ApiLogEvent(
            AuditLogRecord.builder()
                .serviceName(appName)
                .type("OUTGOING_TRANSPORT_ERROR")
                .method(request.method().name())
                .url(request.url().toString())
                .queryString(request.url().getRawQuery())
                .requestHeaders(
                    AuditMetadataFormatter.headers(
                        request.headers(), properties.getCapture().getMaxHeaderSize()))
                .requestBody(NOT_CAPTURED)
                .responseBody(NOT_CAPTURED)
                .duration(System.currentTimeMillis() - start)
                .correlationId(correlationId)
                .errorType(error.getClass().getName())
                .errorMessage(error.getMessage())
                .timestamp(LocalDateTime.now())
                .build()));
  }
}
