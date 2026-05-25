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
                errorType),
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
        .build();
  }
}
