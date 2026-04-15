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
        ApiLogSpecifications.withFilters(now, now, "INCOMING", "test", "CID123");

    when(root.get(anyString())).thenReturn(path);

    spec.toPredicate(root, query, cb);

    // Verify each filter was processed
    verify(cb).equal(root.get("correlationId"), "CID123");
    verify(cb).greaterThanOrEqualTo(root.get("timestamp"), now);
    verify(cb).lessThanOrEqualTo(root.get("timestamp"), now);
    verify(cb).equal(root.get("type"), "INCOMING");
    verify(cb).like(root.get("url"), "%test%");
  }

  @Test
  @DisplayName(
      "GIVEN null filters WHEN toPredicate called THEN cb.and is called with empty array (Branch Coverage)")
  void testWithFilters_NullConditions() {
    // ARRANGE
    Specification<ApiAuditLog> spec =
        ApiLogSpecifications.withFilters(null, null, null, null, null);

    // ACT
    spec.toPredicate(root, query, cb);

    // ASSERT
    // cb.and(new Predicate[0]) is effectively cb.and() in varargs
    verify(cb).and(any(Predicate[].class));
    // OR more strictly:
    verify(cb).and(new Predicate[0]);

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
