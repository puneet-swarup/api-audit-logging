package com.api.audit.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import com.api.audit.util.JsonMasker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiLogListenerTest {

  @Mock private AuditLogStore auditLogStore;
  @Mock private JsonMasker jsonMasker;
  @InjectMocks private ApiLogListener listener;

  @Test
  @DisplayName("GIVEN ApiLogEvent WHEN handleLog THEN mask both bodies and save via store")
  void testHandleLog() {
    AuditLogRecord record =
        AuditLogRecord.builder()
            .serviceName("test-svc")
            .type("INCOMING")
            .method("POST")
            .url("/api/test")
            .requestBody("{\"password\":\"123\"}")
            .responseBody("{\"token\":\"456\"}")
            .httpStatus(200)
            .correlationId("cid-1")
            .timestamp(LocalDateTime.now())
            .build();

    when(jsonMasker.mask("{\"password\":\"123\"}")).thenReturn("{\"password\":\"******\"}");
    when(jsonMasker.mask("{\"token\":\"456\"}")).thenReturn("{\"token\":\"******\"}");

    listener.handleLog(new ApiLogEvent(record));

    verify(jsonMasker, times(2)).mask(any());
    verify(auditLogStore)
        .save(
            argThat(
                saved ->
                    saved.getRequestBody().contains("******")
                        && saved.getResponseBody().contains("******")));
  }

  @Test
  @DisplayName("GIVEN store throws WHEN handleLog THEN exception is swallowed")
  void testHandleLog_storeFailure_doesNotPropagate() {
    AuditLogRecord record =
        AuditLogRecord.builder()
            .serviceName("svc")
            .type("INCOMING")
            .method("GET")
            .url("/test")
            .httpStatus(200)
            .correlationId("x")
            .timestamp(LocalDateTime.now())
            .build();

    when(jsonMasker.mask(any())).thenReturn(null);
    doThrow(new RuntimeException("DB down")).when(auditLogStore).save(any());

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> listener.handleLog(new ApiLogEvent(record)));
  }
}
