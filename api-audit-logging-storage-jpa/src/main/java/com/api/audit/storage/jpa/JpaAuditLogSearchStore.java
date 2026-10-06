package com.api.audit.storage.jpa;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.repository.ApiLogSpecifications;
import com.api.audit.spi.AuditLogSearchStore;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * JPA implementation of {@link AuditLogSearchStore}.
 *
 * <p>Translates search parameters into a JPA {@link
 * org.springframework.data.jpa.domain.Specification} via {@link ApiLogSpecifications} and executes
 * the query via {@link ApiAuditLogRepository}. Maps results back to {@link AuditLogRecord} so
 * callers are storage-agnostic.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class JpaAuditLogSearchStore implements AuditLogSearchStore {

  private final ApiAuditLogRepository repository;

  @Override
  public Page<AuditLogRecord> search(
      String correlationId,
      LocalDateTime start,
      LocalDateTime end,
      String type,
      String url,
      String serviceName,
      String method,
      Integer httpStatus,
      String clientIp,
      String principalName,
      String errorType,
      String tagKey,
      String tagValue,
      Pageable pageable) {
    return repository
        .findAll(
            ApiLogSpecifications.withFilters(
                start,
                end,
                type,
                url,
                correlationId,
                serviceName,
                method,
                httpStatus,
                clientIp,
                principalName,
                errorType,
                tagKey,
                tagValue),
            pageable)
        .map(this::toRecord);
  }

  private AuditLogRecord toRecord(ApiAuditLog entity) {
    return AuditLogRecord.builder()
        .serviceName(entity.getServiceName())
        .type(entity.getType())
        .method(entity.getMethod())
        .description(entity.getDescription())
        .url(entity.getUrl())
        .queryString(entity.getQueryString())
        .requestHeaders(entity.getRequestHeaders())
        .responseHeaders(entity.getResponseHeaders())
        .requestBody(entity.getRequestBody())
        .responseBody(entity.getResponseBody())
        .httpStatus(entity.getHttpStatus())
        .duration(entity.getDuration())
        .correlationId(entity.getCorrelationId())
        .clientIp(entity.getClientIp())
        .userAgent(entity.getUserAgent())
        .principalName(entity.getPrincipalName())
        .errorType(entity.getErrorType())
        .errorMessage(entity.getErrorMessage())
        .timestamp(entity.getTimestamp())
        .tags(parseTags(entity.getTags()))
        .build();
  }

  /**
   * Parses the JSON object string stored in the {@code tags} column back into a map. The format is
   * the compact subset written by {@link JpaAuditLogStore}. Malformed or empty input yields an
   * empty map so a bad row never breaks a search response.
   *
   * @param json the stored JSON object string; may be {@code null}
   * @return a parsed map, or an empty map when there is nothing to parse
   */
  private java.util.Map<String, String> parseTags(String json) {
    if (json == null || json.isBlank()) {
      return java.util.Map.of();
    }
    java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
    String trimmed = json.trim();
    if (trimmed.length() < 2 || trimmed.charAt(0) != '{') {
      return java.util.Map.of();
    }
    String inner = trimmed.substring(1, trimmed.length() - 1);
    if (inner.isBlank()) {
      return java.util.Map.of();
    }
    for (String pair : inner.split(",")) {
      int colon = pair.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = unquote(pair.substring(0, colon));
      String value = unquote(pair.substring(colon + 1));
      result.put(key, value);
    }
    return result;
  }

  private String unquote(String value) {
    String v = value.trim();
    if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
      v = v.substring(1, v.length() - 1);
    }
    return v.replace("\\\"", "\"").replace("\\\\", "\\");
  }
}
