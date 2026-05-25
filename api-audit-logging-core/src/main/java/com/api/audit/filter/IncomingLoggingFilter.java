package com.api.audit.filter;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter responsible for capturing inbound HTTP requests and responses for auditing.
 *
 * <p>This filter performs three critical functions:
 *
 * <ol>
 *   <li><b>Correlation Tracking:</b> Extracts or generates a {@code X-Correlation-ID} and populates
 *       the SLF4J MDC for distributed tracing.
 *   <li><b>Payload Caching:</b> Wraps the request and response to allow streams to be read multiple
 *       times (once by business logic, once by this filter).
 *   <li><b>Asynchronous Auditing:</b> Publishes an {@link ApiLogEvent} containing an {@link
 *       AuditLogRecord} if the request is marked for auditing.
 * </ol>
 *
 * @author Puneet Swarup
 */
@AllArgsConstructor
@Slf4j
public class IncomingLoggingFilter extends OncePerRequestFilter {

  private final ApplicationEventPublisher publisher;
  private final String appName;
  private final AuditLoggingProperties properties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    HttpServletRequest reqToUse = prepareRequestWrapper(req);
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
      processAuditCapture(reqToUse, req, resWrap, startTime, failure);
      resWrap.copyBodyToResponse();
      MDC.clear();
    }
  }

  private HttpServletRequest prepareRequestWrapper(HttpServletRequest req) throws IOException {
    String contentType = req.getContentType();
    String method = req.getMethod();
    boolean isMultipart =
        contentType != null && contentType.toLowerCase().contains("multipart/form-data");
    boolean isUpload =
        isMultipart || "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
    return (isUpload && !isMultipart) ? new EagerRequestWrapper(req) : req;
  }

  private void initializeMdc(HttpServletRequest req) {
    String cid = req.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
    MDC.put(
        CorrelationContext.CORRELATION_ID_HEADER, cid != null ? cid : UUID.randomUUID().toString());
  }

  private void processAuditCapture(
      HttpServletRequest auditReq,
      HttpServletRequest originalReq,
      ContentCachingResponseWrapper resWrap,
      long startTime,
      Exception failure) {
    try {
      if (auditReq.getAttribute("AUDIT_LOG_ENABLED") != null) {
        AuditLogRecord record =
            assembleAuditRecord(auditReq, originalReq, resWrap, startTime, failure);
        publisher.publishEvent(new ApiLogEvent(record));
      }
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
      Exception failure) {
    return AuditLogRecord.builder()
        .serviceName(appName)
        .type(failure == null ? "INCOMING" : "INCOMING_ERROR")
        .method(auditReq.getMethod())
        .description((String) auditReq.getAttribute("AUDIT_LOG_DESC"))
        .url(auditReq.getRequestURI())
        .queryString(auditReq.getQueryString())
        .requestHeaders(
            AuditMetadataFormatter.requestHeaders(
                auditReq, properties.getCapture().getMaxHeaderSize()))
        .responseHeaders(
            AuditMetadataFormatter.responseHeaders(
                resWrap, properties.getCapture().getMaxHeaderSize()))
        .httpStatus(resWrap.getStatus())
        .duration(System.currentTimeMillis() - start)
        .timestamp(LocalDateTime.now())
        .correlationId(MDC.get(CorrelationContext.CORRELATION_ID_HEADER))
        .clientIp(AuditMetadataFormatter.clientIp(auditReq))
        .userAgent(auditReq.getHeader("User-Agent"))
        .principalName(AuditMetadataFormatter.principalName(auditReq))
        .errorType(failure == null ? null : failure.getClass().getName())
        .errorMessage(failure == null ? null : failure.getMessage())
        .requestBody(extractRequestBody(originalReq, auditReq))
        .responseBody(extractResponseBody(resWrap))
        .build();
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
