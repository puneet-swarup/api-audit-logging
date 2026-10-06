package com.api.audit.filter;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.AuditRequestAttributes;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.policy.AuditDecision;
import com.api.audit.policy.AuditDecisionEngine;
import com.api.audit.policy.CaptureMode;
import com.api.audit.policy.SamplingStrategy;
import com.api.audit.spi.AuditMetrics;
import com.api.audit.util.AuditMetadataFormatter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter responsible for capturing inbound HTTP requests and responses for auditing.
 *
 * <p>This filter performs four critical functions:
 *
 * <ol>
 *   <li><b>Decision:</b> asks the {@link AuditDecisionEngine} whether the request should be audited
 *       and, if so, with which description, tags, and {@link CaptureMode}. Non-audited requests
 *       return immediately without allocating request or response wrappers, so the filter is
 *       effectively free for traffic that is not being audited.
 *   <li><b>Correlation Tracking:</b> extracts or generates a {@code X-Correlation-ID} and populates
 *       the SLF4J MDC for distributed tracing.
 *   <li><b>Payload Caching:</b> wraps the request and response to allow streams to be read multiple
 *       times (once by business logic, once by this filter). Wrappers are only created when the
 *       request is being audited.
 *   <li><b>Asynchronous Auditing:</b> publishes an {@link ApiLogEvent} containing an {@link
 *       AuditLogRecord} once the response is available.
 * </ol>
 *
 * <p><b>Backward compatibility:</b> the annotation-based flow is preserved. When a controller is
 * annotated with {@code @AuditLog}, the {@link com.api.audit.interceptor.AuditLogInterceptor} sets
 * a request attribute, the {@link com.api.audit.policy.AnnotationAuditPolicy} turns it into a
 * decision, and this filter captures the request exactly as before. Path-based auditing is an
 * additional, opt-in decision source that requires no application code changes.
 *
 * @author Puneet Swarup
 */
@Slf4j
public class IncomingLoggingFilter extends OncePerRequestFilter {

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;
  private final AuditDecisionEngine decisionEngine;
  private final SamplingStrategy samplingStrategy;
  private final AuditMetrics auditMetrics;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * Creates the filter.
   *
   * @param publisher publishes audit events to the async listener
   * @param appName the service name recorded on every audit record
   * @param properties the library configuration properties
   * @param decisionEngine resolves whether a request is audited
   */
  public IncomingLoggingFilter(
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
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return isExcluded(path) || !isIncluded(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String path = req.getRequestURI();
    AuditDecision decision = decisionEngine.decide(req, path);
    boolean annotationMayApply = properties.getPolicies().getAnnotation().isEnabled();

    if (!decision.isAudit() && !annotationMayApply) {
      // Not audited: do not allocate wrappers, do not touch MDC. Keep the request path cheap.
      chain.doFilter(req, res);
      return;
    }

    HttpServletRequest reqToUse = prepareRequestWrapper(req, decision.getCaptureMode());
    ContentCachingResponseWrapper resWrap = new ContentCachingResponseWrapper(res);

    initializeMdc(req);
    long startTime = System.currentTimeMillis();
    Exception failure = null;

    try {
      chain.doFilter(reqToUse, resWrap);
    } catch (IOException | ServletException | RuntimeException ex) {
      failure = ex;
      throw ex;
    } finally {
      // Phase 2 decision: the annotation interceptor has run by now, so the engine can see the
      // AUDIT_LOG_ENABLED attribute and produce the final decision. Path rules still apply.
      AuditDecision finalDecision = decisionEngine.decide(req, path);
      if (finalDecision.isAudit()) {
        processAuditCapture(reqToUse, req, resWrap, startTime, failure, finalDecision);
      }
      resWrap.copyBodyToResponse();
      MDC.clear();
    }
  }

  private HttpServletRequest prepareRequestWrapper(HttpServletRequest req, CaptureMode mode)
      throws IOException {
    if (mode == CaptureMode.METADATA_ONLY) {
      // Bodies are not captured, so there is no need to eagerly buffer them.
      return req;
    }
    String contentType = req.getContentType();
    String method = req.getMethod();
    boolean isMultipart =
        contentType != null && contentType.toLowerCase().contains("multipart/form-data");
    boolean isUpload =
        isMultipart || "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
    return (isUpload && !isMultipart) ? new EagerRequestWrapper(req) : req;
  }

  private boolean isIncluded(String path) {
    return properties.getCapture().getIncludedPaths().stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private boolean isExcluded(String path) {
    return properties.getCapture().getExcludedPaths().stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private void initializeMdc(HttpServletRequest req) {
    String cid = sanitizeCorrelationId(req.getHeader(CorrelationContext.CORRELATION_ID_HEADER));
    MDC.put(
        CorrelationContext.CORRELATION_ID_HEADER, cid != null ? cid : UUID.randomUUID().toString());
  }

  /**
   * Sanitises an inbound correlation ID before it is placed in the MDC and persisted.
   *
   * <p>The header is caller-controlled, so it is clamped in length and restricted to a safe
   * character set to prevent log injection, MDC pollution, and oversized records. A value that is
   * blank or contains only unsafe characters is discarded so a fresh UUID is generated instead.
   *
   * @param raw the raw header value; may be {@code null}
   * @return a safe value, or {@code null} when none could be derived
   */
  static String sanitizeCorrelationId(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.strip();
    if (trimmed.isEmpty()) {
      return null;
    }
    StringBuilder safe = new StringBuilder(Math.min(trimmed.length(), 128));
    for (int i = 0; i < trimmed.length() && safe.length() < 128; i++) {
      char c = trimmed.charAt(i);
      boolean allowed =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '-'
              || c == '_'
              || c == '.'
              || c == ':';
      if (allowed) {
        safe.append(c);
      }
    }
    return safe.length() == 0 ? null : safe.toString();
  }

  private void processAuditCapture(
      HttpServletRequest auditReq,
      HttpServletRequest originalReq,
      ContentCachingResponseWrapper resWrap,
      long startTime,
      Exception failure,
      AuditDecision decision) {
    try {
      AuditLogRecord record =
          assembleAuditRecord(auditReq, originalReq, resWrap, startTime, failure, decision);
      if (!samplingStrategy.shouldStore(record)) {
        // Sampled out: record the drop and skip publishing.
        auditMetrics.recordDropped("SAMPLED", record);
        return;
      }
      publisher.publishEvent(new ApiLogEvent(record));
    } catch (Exception e) {
      log.error(
          "Audit Logging failed for correlationId {}: {}",
          MDC.get(CorrelationContext.CORRELATION_ID_HEADER),
          e.getMessage());
    }
  }

  /**
   * Maps request and response data into an immutable {@link AuditLogRecord}. Note: masking is NOT
   * applied here — it happens in {@link com.api.audit.listener.ApiLogListener} so all storage
   * backends benefit from it automatically.
   */
  private AuditLogRecord assembleAuditRecord(
      HttpServletRequest auditReq,
      HttpServletRequest originalReq,
      ContentCachingResponseWrapper resWrap,
      long start,
      Exception failure,
      AuditDecision decision) {

    CaptureMode mode = decision.getCaptureMode();
    boolean captureHeaders = mode == CaptureMode.FULL || mode == CaptureMode.METADATA_ONLY;
    boolean captureBodies = mode == CaptureMode.FULL || mode == CaptureMode.BODY_ONLY;

    return AuditLogRecord.builder()
        .serviceName(appName)
        .type(failure == null ? "INCOMING" : "INCOMING_ERROR")
        .method(auditReq.getMethod())
        .description(resolveDescription(auditReq, decision))
        .url(auditReq.getRequestURI())
        .queryString(auditReq.getQueryString())
        .requestHeaders(
            captureHeaders
                ? AuditMetadataFormatter.requestHeaders(
                    auditReq, properties.getCapture().getMaxHeaderSize())
                : null)
        .responseHeaders(
            captureHeaders
                ? AuditMetadataFormatter.responseHeaders(
                    resWrap, properties.getCapture().getMaxHeaderSize())
                : null)
        .httpStatus(resWrap.getStatus())
        .duration(System.currentTimeMillis() - start)
        .timestamp(LocalDateTime.now())
        .correlationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER))
        .clientIp(AuditMetadataFormatter.clientIp(auditReq))
        .userAgent(auditReq.getHeader("User-Agent"))
        .principalName(AuditMetadataFormatter.principalName(auditReq))
        .errorType(failure == null ? null : failure.getClass().getName())
        .errorMessage(failure == null ? null : failure.getMessage())
        .requestBody(captureBodies ? extractRequestBody(originalReq, auditReq) : null)
        .responseBody(captureBodies ? extractResponseBody(resWrap) : null)
        .tags(decision.getTags())
        .build();
  }

  /**
   * Resolves the description for the record. Precedence: the policy engine's description (path rule
   * or annotation) wins; otherwise fall back to a synthetic {@code "<METHOD> <path>"} label.
   */
  private String resolveDescription(HttpServletRequest req, AuditDecision decision) {
    if (decision.getDescription() != null && !decision.getDescription().isBlank()) {
      return decision.getDescription();
    }
    Object annotated = req.getAttribute(AuditRequestAttributes.AUDIT_LOG_DESC);
    if (annotated != null && !annotated.toString().isBlank()) {
      return annotated.toString();
    }
    return req.getMethod() + " " + req.getRequestURI();
  }

  private String extractRequestBody(HttpServletRequest req, HttpServletRequest wrappedReq) {
    String contentType = req.getContentType();
    boolean isMultipart =
        contentType != null && contentType.toLowerCase().contains("multipart/form-data");

    if (isMultipart) {
      String params = formatParameterMap(req.getParameterMap());
      return "[MULTIPART] " + (params.isEmpty() ? "File Content Only" : params);
    }

    if (wrappedReq instanceof EagerRequestWrapper eager) {
      byte[] body = eager.getBody();
      int maxBodySize = properties.getCapture().getMaxBodySize();
      return body.length > maxBodySize
          ? "[REQUEST TOO LARGE: " + body.length + " bytes]"
          : new String(body, StandardCharsets.UTF_8);
    }

    return "[NOT CACHED]";
  }

  private String extractResponseBody(ContentCachingResponseWrapper resWrap) {
    String resType = resWrap.getContentType();

    if (resType == null) {
      return "[NO CONTENT TYPE]";
    }

    String lower = resType.toLowerCase();

    if (lower.contains("text/event-stream")
        || lower.contains("application/stream")
        || lower.contains("application/octet-stream")
        || lower.contains("multipart/")) {
      return "[STREAMING CONTENT NOT LOGGED]";
    }

    boolean isTextual = lower.contains("json") || lower.contains("text") || lower.contains("xml");

    if (!isTextual) {
      return "[NON-TEXTUAL CONTENT NOT LOGGED]";
    }

    byte[] content = resWrap.getContentAsByteArray();
    int maxBodySize = properties.getCapture().getMaxBodySize();
    return content.length > maxBodySize
        ? "[RESPONSE TOO LARGE: " + content.length + " bytes]"
        : new String(content, StandardCharsets.UTF_8);
  }

  private String formatParameterMap(Map<String, String[]> parameterMap) {
    if (parameterMap == null || parameterMap.isEmpty()) return "";
    return parameterMap.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
        .collect(Collectors.joining("&"));
  }
}
