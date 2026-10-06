package com.api.audit.storage.jpa;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.repository.ApiLogSpecifications;
import com.api.audit.spi.AuditLogSearchStore;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * JPA implementation of {@link AuditLogSearchStore}.
 *
 * <p>Translates a framework-neutral {@link AuditLogQuery} into a Spring Data {@link Pageable} and a
 * JPA {@link org.springframework.data.jpa.domain.Specification} via {@link ApiLogSpecifications},
 * executes the query through {@link ApiAuditLogRepository}, and converts the Spring Data {@link
 * Page} back into the framework-neutral {@link AuditLogPage}. Spring Data stays entirely inside
 * this module; the SPI does not see it.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class JpaAuditLogSearchStore implements AuditLogSearchStore {

  private final ApiAuditLogRepository repository;

  @Override
  public AuditLogPage<AuditLogRecord> search(AuditLogQuery query) {
    Page<ApiAuditLog> page =
        repository.findAll(
            ApiLogSpecifications.withFilters(
                query.getStart(),
                query.getEnd(),
                query.getType(),
                query.getUrl(),
                query.getCorrelationId(),
                query.getServiceName(),
                query.getMethod(),
                query.getHttpStatus(),
                query.getClientIp(),
                query.getPrincipalName(),
                query.getErrorType(),
                query.getTagKey(),
                query.getTagValue()),
            toPageable(query));

    return new AuditLogPage<>(
        page.getContent().stream().map(this::toRecord).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  private Pageable toPageable(AuditLogQuery query) {
    Sort.Direction direction = query.isSortAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, query.getSortBy() == null ? "timestamp" : query.getSortBy());
    return PageRequest.of(query.effectivePage(), query.effectiveSize(), sort);
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
  private Map<String, String> parseTags(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    String trimmed = json.trim();
    if (trimmed.length() < 2 || trimmed.charAt(0) != '{') {
      return Map.of();
    }
    String inner = trimmed.substring(1, trimmed.length() - 1);
    if (inner.isBlank()) {
      return Map.of();
    }
    for (String pair : inner.split(",")) {
      int colon = pair.indexOf(':');
      if (colon < 0) {
        continue;
      }
      result.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
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
