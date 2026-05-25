package com.api.audit.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.api.audit.entity.ApiAuditLog;
import jakarta.persistence.criteria.*;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ApiLogSpecificationsTest {

  @Mock private Root<ApiAuditLog> root;
  @Mock private CriteriaQuery<?> query;
  @Mock private CriteriaBuilder cb;
  @Mock private Path<Object> path;

  @Test
  @DisplayName(
      "GIVEN all filters are provided WHEN toPredicate called THEN all predicates are added")
  void testWithFilters_AllConditions() {
    LocalDateTime now = LocalDateTime.now();
    Specification<ApiAuditLog> spec =
        ApiLogSpecifications.withFilters(
            now,
            now,
            "INCOMING",
            "test",
            "CID123",
            "demo-service",
            "GET",
            200,
            "203.0.113.10",
            "puneet",
            "HTTP_500");

    when(root.get(anyString())).thenReturn(path);

    spec.toPredicate(root, query, cb);

    verify(cb).equal(root.get("correlationId"), "CID123");
    verify(cb).greaterThanOrEqualTo(root.get("timestamp"), now);
    verify(cb).lessThanOrEqualTo(root.get("timestamp"), now);
    verify(cb).equal(root.get("type"), "INCOMING");
    verify(cb).like(root.get("url"), "%test%");
    verify(cb).equal(root.get("serviceName"), "demo-service");
    verify(cb).equal(root.get("method"), "GET");
    verify(cb).equal(root.get("httpStatus"), 200);
    verify(cb).equal(root.get("clientIp"), "203.0.113.10");
    verify(cb).equal(root.get("principalName"), "puneet");
    verify(cb).equal(root.get("errorType"), "HTTP_500");
  }

  @Test
  @DisplayName("GIVEN null filters WHEN toPredicate called THEN conjunction predicate is returned")
  void testWithFilters_NullConditions() {
    Specification<ApiAuditLog> spec =
        ApiLogSpecifications.withFilters(
            null, null, null, null, null, null, null, null, null, null, null);

    spec.toPredicate(root, query, cb);

    verify(cb).conjunction();
    verifyNoInteractions(root);
  }

  @Test
  @DisplayName("Private constructor coverage")
  void testConstructor() throws Exception {
    Constructor<ApiLogSpecifications> constructor =
        ApiLogSpecifications.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertNotNull(constructor.newInstance());
  }
}
