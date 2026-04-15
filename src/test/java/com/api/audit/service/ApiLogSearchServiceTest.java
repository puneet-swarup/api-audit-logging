package com.api.audit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.api.audit.entity.ApiAuditLog;
import com.api.audit.repository.ApiAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ApiLogSearchServiceTest {

  @Mock private ApiAuditLogRepository repository;
  @InjectMocks private ApiLogSearchService service;

  @Test
  @DisplayName("GIVEN search params WHEN search is called THEN repository findAll is triggered")
  @SuppressWarnings("unchecked")
  void testSearch() {
    Page<ApiAuditLog> mockPage = mock(Page.class);
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

    service.search(null, null, null, null, null, Pageable.unpaged());

    verify(repository).findAll(any(Specification.class), any(Pageable.class));
  }
}
