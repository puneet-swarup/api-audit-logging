package com.api.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.spi.AuditLogSearchStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link ApiLogController}.
 *
 * <p>Verifies that the controller translates HTTP pagination into the framework-neutral {@link
 * AuditLogQuery} and returns a 200 with the store result.
 *
 * @author Puneet Swarup
 */
@ExtendWith(MockitoExtension.class)
class ApiLogControllerTest {

  @Mock private AuditLogSearchStore searchStore;
  @InjectMocks private ApiLogController controller;

  @Test
  @DisplayName("GIVEN GET request WHEN getLogs called THEN return 200 OK with the store result")
  void testGetLogs() {
    when(searchStore.search(any(AuditLogQuery.class))).thenReturn(AuditLogPage.empty(0, 20));

    ResponseEntity<AuditLogPage<com.api.audit.model.AuditLogRecord>> response =
        controller.getLogs(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Pageable.unpaged());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(searchStore, times(1)).search(any(AuditLogQuery.class));
  }

  @Test
  @DisplayName("GIVEN filter params WHEN getLogs called THEN they are mapped into the query")
  void filtersAreMappedIntoQuery() {
    when(searchStore.search(any(AuditLogQuery.class))).thenReturn(AuditLogPage.empty(0, 20));

    controller.getLogs(
        "corr-1",
        null,
        null,
        "INCOMING",
        "/api",
        "svc",
        "GET",
        200,
        "10.0.0.1",
        "puneet",
        "HTTP_500",
        "module",
        "payments",
        Pageable.unpaged());

    ArgumentCaptor<AuditLogQuery> captor = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(searchStore).search(captor.capture());
    AuditLogQuery query = captor.getValue();
    assertThat(query.getCorrelationId()).isEqualTo("corr-1");
    assertThat(query.getType()).isEqualTo("INCOMING");
    assertThat(query.getTagKey()).isEqualTo("module");
    assertThat(query.getTagValue()).isEqualTo("payments");
    assertThat(query.getHttpStatus()).isEqualTo(200);
  }
}
