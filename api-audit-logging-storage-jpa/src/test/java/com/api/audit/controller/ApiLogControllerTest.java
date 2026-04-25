package java.com.api.audit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.api.audit.service.ApiLogSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ApiLogControllerTest {

  @Mock private ApiLogSearchService searchService;
  @InjectMocks private ApiLogController controller;

  @Test
  @DisplayName("GIVEN GET request WHEN getLogs called THEN return 200 OK and search results")
  void testGetLogs() {
    Mockito.when(searchService.search(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

    ResponseEntity<?> response =
        controller.getLogs(null, null, null, null, null, Pageable.unpaged());

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    Mockito.verify(searchService).search(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
  }
}
