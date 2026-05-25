package com.api.audit.storage.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.repository.ApiAuditLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for mapping storage-agnostic audit records into the JPA entity.
 *
 * <p>The mapper is intentionally covered because every new captured field must be carried into the
 * default production store. Missing a field here would make the capture layer look correct while
 * silently dropping data at persistence time.
 *
 * @author Puneet Swarup
 */
class JpaAuditLogStoreTest {

  @Test
  void mapsMetadataFieldsToJpaEntity() {
    ApiAuditLogRepository repository = org.mockito.Mockito.mock(ApiAuditLogRepository.class);
    JpaAuditLogStore store = new JpaAuditLogStore(repository);
    LocalDateTime timestamp = LocalDateTime.of(2026, 5, 25, 4, 30);

    AuditLogRecord record =
        AuditLogRecord.builder()
            .serviceName("order-service")
            .type("INCOMING")
            .method("GET")
            .description("GET_ORDER")
            .url("/orders/42")
            .queryString("expand=items")
            .requestHeaders("{\"User-Agent\":[\"JUnit\"]}")
            .responseHeaders("{\"Content-Type\":[\"application/json\"]}")
            .requestBody("")
            .responseBody("{\"id\":42}")
            .httpStatus(200)
            .duration(12)
            .correlationId("corr-jpa-1")
            .clientIp("203.0.113.10")
            .userAgent("JUnit")
            .principalName("puneet")
            .errorType("java.lang.IllegalStateException")
            .errorMessage("Example failure")
            .timestamp(timestamp)
            .build();

    store.save(record);

    ArgumentCaptor<ApiAuditLog> captor = ArgumentCaptor.forClass(ApiAuditLog.class);
    verify(repository).save(captor.capture());
    ApiAuditLog entity = captor.getValue();

    assertThat(entity.getQueryString()).isEqualTo("expand=items");
    assertThat(entity.getRequestHeaders()).contains("User-Agent");
    assertThat(entity.getResponseHeaders()).contains("Content-Type");
    assertThat(entity.getClientIp()).isEqualTo("203.0.113.10");
    assertThat(entity.getUserAgent()).isEqualTo("JUnit");
    assertThat(entity.getPrincipalName()).isEqualTo("puneet");
    assertThat(entity.getErrorType()).isEqualTo("java.lang.IllegalStateException");
    assertThat(entity.getErrorMessage()).isEqualTo("Example failure");
  }
}
