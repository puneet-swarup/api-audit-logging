package com.api.audit.filter;

import com.api.audit.context.CorrelationContext;
import com.api.audit.entity.ApiAuditLog;
import com.api.audit.event.ApiLogEvent;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter responsible for capturing inbound HTTP requests and responses for auditing.
 *
 * <p>This filter performs three critical functions:
 *
 * <ol>
 *   <li><b>Correlation Tracking:</b> Extracts or generates a {@code X-Correlation-ID} and populates
 *       the SLF4J MDC for distributed tracing.
 *   <li><b>Payload Caching:</b> Wraps the request and response in {@link
 *       ContentCachingRequestWrapper} and {@link ContentCachingResponseWrapper} to allow the
 *       streams to be read multiple times (once by the business logic and once by the audit
 *       logger).
 *   <li><b>Asynchronous Auditing:</b> Publishes an {@link ApiLogEvent} if the request is marked for
 *       auditing via request attributes.
 * </ol>
 *
 * @author Puneet Swarup
 * @see CorrelationContext
 * @see OncePerRequestFilter
 */
@AllArgsConstructor
@Slf4j
public class IncomingLoggingFilter extends OncePerRequestFilter {

  private final ApplicationEventPublisher publisher;
  private final String appName;

  private static final int MAX_PAYLOAD_SIZE = 1024 * 1024;

  /**
   * Entry point for the filter. Orchestrates the wrapping, execution, and audit logging.
   *
   * @param req The inbound {@link HttpServletRequest}.
   * @param res The outbound {@link HttpServletResponse}.
   * @param chain The {@link FilterChain} to continue execution.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    HttpServletRequest reqToUse = prepareRequestWrapper(req);
    ContentCachingResponseWrapper resWrap = new ContentCachingResponseWrapper(res);

    initializeMdc(req);
    long startTime = System.currentTimeMillis();

    try {
      chain.doFilter(reqToUse, resWrap);
    } finally {
      processAuditCapture(req, reqToUse, resWrap, startTime);
      resWrap.copyBodyToResponse();
      MDC.clear();
    }
  }

  /**
   * Determines the appropriate request wrapper. Bypasses {@link EagerRequestWrapper} for
   * multipart/form-data to prevent breaking file upload parsers and saving memory.
   *
   * @param req The original request.
   * @return A wrapped request or the original request if multipart.
   * @throws IOException If the stream cannot be read.
   */
  private HttpServletRequest prepareRequestWrapper(HttpServletRequest req) throws IOException {
    String contentType = req.getContentType();
    String method = req.getMethod();

    boolean isMultipart =
        contentType != null && contentType.toLowerCase().contains("multipart/form-data");
    boolean isUpload =
        isMultipart || "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);

    return (isUpload && !isMultipart) ? new EagerRequestWrapper(req) : req;
  }

  /**
   * Populates the MDC with a Correlation ID for traceability across logs.
   *
   * @param req The inbound request to check for existing headers.
   */
  private void initializeMdc(HttpServletRequest req) {
    String cid = req.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
    MDC.put(
        CorrelationContext.CORRELATION_ID_HEADER, cid != null ? cid : UUID.randomUUID().toString());
  }

  /**
   * Validates if auditing is required and triggers the asynchronous audit event.
   *
   * @param originalReq The unwrapped request containing metadata.
   * @param wrappedReq The potentially wrapped request containing the body.
   * @param resWrap The wrapped response.
   * @param startTime The epoch time when the request started.
   */
  private void processAuditCapture(
      HttpServletRequest originalReq,
      HttpServletRequest wrappedReq,
      ContentCachingResponseWrapper resWrap,
      long startTime) {
    try {
      if (originalReq.getAttribute("AUDIT_LOG_ENABLED") != null) {
        ApiAuditLog audit = assembleAuditLog(originalReq, wrappedReq, resWrap, startTime);
        publisher.publishEvent(new ApiLogEvent(audit));
      }
    } catch (Exception e) {
      log.error(
          "Audit Logging failed for correlationId {}: {}",
          MDC.get(CorrelationContext.CORRELATION_ID_HEADER),
          e.getMessage());
    }
  }

  /**
   * Maps request and response data into the {@link ApiAuditLog} entity.
   *
   * @return A fully populated audit log object.
   */
  private ApiAuditLog assembleAuditLog(
      HttpServletRequest req,
      HttpServletRequest wrappedReq,
      ContentCachingResponseWrapper resWrap,
      long start) {
    ApiAuditLog audit = new ApiAuditLog();
    audit.setServiceName(appName);
    audit.setType("INCOMING");
    audit.setMethod(req.getMethod());
    audit.setDescription((String) req.getAttribute("AUDIT_LOG_DESC"));
    audit.setUrl(req.getRequestURI());
    audit.setHttpStatus(resWrap.getStatus());
    audit.setDuration(System.currentTimeMillis() - start);
    audit.setTimestamp(LocalDateTime.now());
    audit.setCorrelationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER));

    audit.setRequestBody(extractRequestBody(req, wrappedReq));
    audit.setResponseBody(extractResponseBody(resWrap));

    return audit;
  }

  /**
   * Safely extracts the request body. If multipart, it logs form parameters only. Implements size
   * truncation to prevent Heap exhaustion.
   *
   * @param req The original request (for parameters).
   * @param wrappedReq The request wrapper (for body bytes).
   * @return A sanitized string representation of the request payload.
   */
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
      return body.length > MAX_PAYLOAD_SIZE
          ? "[REQUEST TOO LARGE]"
          : new String(body, StandardCharsets.UTF_8);
    }

    return "[NOT CACHED]";
  }

  /**
   * Safely extracts the response body. Only logs textual content types (JSON/Text/XML) and
   * truncates data exceeding the 1MB safety limit.
   *
   * @param resWrap The response wrapper containing the buffered body.
   * @return A sanitized string representation of the response payload.
   */
  private String extractResponseBody(ContentCachingResponseWrapper resWrap) {
    String resType = resWrap.getContentType();
    boolean isTextual =
        resType != null
            && (resType.contains("json") || resType.contains("text") || resType.contains("xml"));

    if (!isTextual) {
      return "[NON-TEXTUAL CONTENT NOT LOGGED]";
    }

    byte[] content = resWrap.getContentAsByteArray();
    return content.length > MAX_PAYLOAD_SIZE
        ? "[RESPONSE TOO LARGE: " + content.length + " bytes]"
        : new String(content, StandardCharsets.UTF_8);
  }

  /** Utility to serialize the HttpServletRequest parameter map into a query-string format. */
  private String formatParameterMap(Map<String, String[]> parameterMap) {
    if (parameterMap == null || parameterMap.isEmpty()) return "";
    return parameterMap.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
        .collect(Collectors.joining("&"));
  }
}
