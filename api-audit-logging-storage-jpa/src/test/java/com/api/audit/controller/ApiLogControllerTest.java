package com.api.audit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.audit.spi.AuditLogSearchStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ApiLogControllerTest {

  @Mock private AuditLogSearchStore searchStore;
  @InjectMocks private ApiLogController controller;

  @Test
  @DisplayName("GIVEN GET request WHEN getLogs called THEN return 200 OK")
  void testGetLogs() {
    when(searchStore.search(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Page.empty());

    ResponseEntity<?> response =
        controller.getLogs(
            null, null, null, null, null, null, null, null, null, null, null, Pageable.unpaged());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(searchStore, times(1))
        .search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }
}
