package com.api.audit.storage.jpa;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.spi.AuditLogStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA implementation of {@link AuditLogStore}.
 *
 * <p>Maps an {@link AuditLogRecord} (plain model) to an {@link ApiAuditLog} JPA entity and persists
 * it via {@link ApiAuditLogRepository}.
 *
 * <p>This is the default store when {@code spring-boot-starter-data-jpa} is on the classpath.
 * Activated by the autoconfigure module via {@code @ConditionalOnMissingBean(AuditLogStore.class)}.
 *
 * @author Puneet Swarup
 */
@Slf4j
@RequiredArgsConstructor
public class JpaAuditLogStore implements AuditLogStore {

  private final ApiAuditLogRepository repository;

  @Override
  public void save(AuditLogRecord record) {
    repository.save(toEntity(record));
  }

  /**
   * Maps an {@link AuditLogRecord} to an {@link ApiAuditLog} JPA entity. The type is stored as a
   * String in the database for readability.
   */
  private ApiAuditLog toEntity(AuditLogRecord record) {
    ApiAuditLog entity = new ApiAuditLog();
    entity.setServiceName(record.getServiceName());
    entity.setType(record.getType());
    entity.setMethod(record.getMethod());
    entity.setDescription(record.getDescription());
    entity.setUrl(record.getUrl());
    entity.setQueryString(record.getQueryString());
    entity.setRequestHeaders(record.getRequestHeaders());
    entity.setResponseHeaders(record.getResponseHeaders());
    entity.setRequestBody(record.getRequestBody());
    entity.setResponseBody(record.getResponseBody());
    entity.setHttpStatus(record.getHttpStatus());
    entity.setDuration(record.getDuration());
    entity.setCorrelationId(record.getCorrelationId());
    entity.setClientIp(record.getClientIp());
    entity.setUserAgent(record.getUserAgent());
    entity.setPrincipalName(record.getPrincipalName());
    entity.setErrorType(record.getErrorType());
    entity.setErrorMessage(record.getErrorMessage());
    entity.setTimestamp(record.getTimestamp());
    entity.setTags(serializeTags(record.getTags()));
    return entity;
  }

  /**
   * Serializes the tag map to a compact JSON object string without pulling a JSON library into this
   * module. Keys and values are escaped minimally; tags are operator-controlled configuration, not
   * untrusted user input, so a full JSON writer is not required here.
   *
   * @param tags the tag map; may be {@code null} or empty
   * @return a JSON object string, or {@code null} when there are no tags
   */
  private String serializeTags(java.util.Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (java.util.Map.Entry<String, String> entry : tags.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(escape(entry.getKey())).append("\":\"");
      sb.append(escape(entry.getValue())).append('"');
    }
    return sb.append('}').toString();
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
