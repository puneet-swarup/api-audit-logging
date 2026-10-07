package com.api.audit.webflux;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.policy.AuditDecision;
import com.api.audit.policy.AuditDecisionEngine;
import com.api.audit.policy.SamplingStrategy;
import com.api.audit.spi.AuditMetrics;
import com.api.audit.util.AuditMetadataFormatter;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive inbound capture filter for WebFlux applications.
 *
 * <p>Mirrors the servlet {@code IncomingLoggingFilter} for reactive apps: it asks the {@link
 * AuditDecisionEngine} whether the request should be audited (path rules and any registered
 * policy), sets up a correlation ID in the reactor context, and publishes an {@link ApiLogEvent}
 * when the exchange completes.
 *
 * <p><b>Correlation ID.</b> Reactive apps do not have thread-local MDC across operators, so the
 * correlation ID is carried in the reactor {@link reactor.util.context.Context} under {@link
 * #CORRELATION_CONTEXT_KEY} and also written to the response header for propagation.
 *
 * <p><b>Bodies.</b> Request and response bodies are not consumed here. Reading them safely in a
 * reactive pipeline requires re-publishing, which the library intentionally leaves out of the
 * inbound filter. Metadata (method, URL, status, duration, headers, identity) is captured.
 *
 * @author Puneet Swarup
 */
@Slf4j
public class ReactiveIncomingAuditFilter implements WebFilter {

  /** Reactor context key under which the correlation ID is stored. */
  public static final String CORRELATION_CONTEXT_KEY = "auditCorrelationId";

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;
  private final AuditDecisionEngine decisionEngine;
  private final SamplingStrategy samplingStrategy;
  private final AuditMetrics auditMetrics;

  /**
   * Creates the filter.
   *
   * @param publisher publishes audit events
   * @param appName the service name recorded on every record
   * @param properties the library configuration properties
   * @param decisionEngine resolves whether a request is audited
   * @param samplingStrategy decides whether a captured record is stored
   * @param auditMetrics reports dropped records
   */
  public ReactiveIncomingAuditFilter(
      ApplicationEventPublisher publisher,
      String appName,
      AuditLoggingProperties properties,
      AuditDecisionEngine decisionEngine,
      SamplingStrategy samplingStrategy,
      AuditMetrics auditMetrics) {
    this.publisher = publisher;
    this.appName = appName;
    this.properties = properties;
    this.decisionEngine = decisionEngine;
    this.samplingStrategy = samplingStrategy;
    this.auditMetrics = auditMetrics;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getRawPath();
    ReactiveAuditRequest auditRequest = new ReactiveAuditRequest(exchange);

    AuditDecision decision = decisionEngine.decide(auditRequest, path);
    if (!decision.isAudit()) {
      return chain.filter(exchange);
    }

    String correlationId = resolveCorrelationId(request);
    long start = System.currentTimeMillis();

    exchange
        .getResponse()
        .getHeaders()
        .add(CorrelationContext.CORRELATION_ID_HEADER, correlationId);

    return chain
        .filter(exchange)
        .contextWrite(ctx -> ctx.put(CORRELATION_CONTEXT_KEY, correlationId))
        .doOnEach(
            signal -> {
              if (signal.isOnComplete() || signal.isOnError()) {
                capture(exchange, correlationId, start, signal.getThrowable(), decision);
              }
            });
  }

  private String resolveCorrelationId(ServerHttpRequest request) {
    String existing = request.getHeaders().getFirst(CorrelationContext.CORRELATION_ID_HEADER);
    return existing != null && !existing.isBlank() ? existing : UUID.randomUUID().toString();
  }

  private void capture(
      ServerWebExchange exchange,
      String correlationId,
      long start,
      Throwable failure,
      AuditDecision decision) {
    try {
      ServerHttpRequest request = exchange.getRequest();
      ServerHttpResponse response = exchange.getResponse();
      Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();

      AuditLogRecord record =
          AuditLogRecord.builder()
              .serviceName(appName)
              .type(failure == null ? "INCOMING" : "INCOMING_ERROR")
              .method(request.getMethod().name())
              .description(decision.getDescription())
              .url(request.getURI().getRawPath())
              .queryString(request.getURI().getRawQuery())
              .requestHeaders(
                  AuditMetadataFormatter.headers(
                      request.getHeaders(), properties.getCapture().getMaxHeaderSize()))
              .responseHeaders(
                  AuditMetadataFormatter.headers(
                      response.getHeaders(), properties.getCapture().getMaxHeaderSize()))
              .httpStatus(status)
              .duration(System.currentTimeMillis() - start)
              .correlationId(correlationId)
              .errorType(failure == null ? null : failure.getClass().getName())
              .errorMessage(failure == null ? null : failure.getMessage())
              .timestamp(LocalDateTime.now())
              .build();

      if (!samplingStrategy.shouldStore(record)) {
        auditMetrics.recordDropped("SAMPLED", record);
        return;
      }
      publisher.publishEvent(new ApiLogEvent(record));
    } catch (Exception ex) {
      log.error(
          "[AuditLog] Reactive capture failed for correlationId={}: {}",
          correlationId,
          ex.getMessage());
    } finally {
      MDC.remove(CorrelationContext.CORRELATION_ID_HEADER);
    }
  }
}
