package com.api.audit.listener;

import static org.mockito.Mockito.*;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.util.JsonMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiLogListenerTest {

  @Mock private ApiAuditLogRepository repository;
  @Mock private JsonMasker jsonMasker;
  @InjectMocks private ApiLogListener listener;

  @Test
  @DisplayName("GIVEN ApiLogEvent WHEN handleLog called THEN mask data and save to repository")
  void testHandleLog() {
    ApiAuditLog audit = new ApiAuditLog();
    audit.setRequestBody("{\"password\":\"123\"}");
    audit.setResponseBody("{\"token\":\"456\"}");
    ApiLogEvent event = new ApiLogEvent(audit);

    // Stub masker to return a masked value (simulating real behaviour)
    when(jsonMasker.mask("{\"password\":\"123\"}")).thenReturn("{\"password\":\"******\"}");
    when(jsonMasker.mask("{\"token\":\"456\"}")).thenReturn("{\"token\":\"******\"}");

    listener.handleLog(event);

    verify(jsonMasker, times(2)).mask(any());
    verify(repository)
        .save(
            argThat(
                log ->
                    log.getRequestBody().contains("******")
                        && log.getResponseBody().contains("******")));
  }
}
