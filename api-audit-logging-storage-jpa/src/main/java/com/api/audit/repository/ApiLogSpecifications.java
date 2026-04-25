package com.api.audit.repository;

import com.api.audit.entity.ApiAuditLog;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Static factory for building dynamic JPA Specifications for {@link ApiAuditLog} queries.
 *
 * <p>This utility leverages the JPA Criteria API to generate type-safe {@code WHERE} clauses based
 * on optional search parameters. It is primarily consumed by the {@code ApiLogSearchService} to
 * provide flexible filtering capabilities for the administrative log viewer.
 *
 * @author Puneet Swarup
 * @see org.springframework.data.jpa.domain.Specification
 * @see jakarta.persistence.criteria.CriteriaBuilder
 */
public class ApiLogSpecifications {

  /** Private constructor to prevent instantiation of this utility class. */
  private ApiLogSpecifications() {
    // Utility class pattern
  }

  /**
   * Constructs a composite specification based on the provided filter criteria.
   *
   * <p>All parameters are optional. If a parameter is {@code null}, it is excluded from the
   * resulting query. If all parameters are {@code null}, the specification effectively represents
   * an "unfiltered" query.
   *
   * <p><b>Filter Logic:</b>
   *
   * <ul>
   *   <li>{@code correlationId}: Exact match.
   *   <li>{@code start/end}: Inclusive range check on the {@code timestamp} field.
   *   <li>{@code type}: Exact match (e.g., "INCOMING", "OUTGOING").
   *   <li>{@code url}: Partial match (SQL {@code LIKE %value%}).
   * </ul>
   *
   * @param start minimum inclusive timestamp
   * @param end maximum inclusive timestamp
   * @param type the classification of the log
   * @param url a string snippet to match against the request URL
   * @param correlationId the unique distributed tracing ID
   * @return a {@link Specification} object suitable for use with {@link ApiAuditLogRepository}
   */
  public static Specification<ApiAuditLog> withFilters(
      LocalDateTime start, LocalDateTime end, String type, String url, String correlationId) {

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (correlationId != null) {
        predicates.add(cb.equal(root.get("correlationId"), correlationId));
      }
      if (start != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), start));
      }
      if (end != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), end));
      }
      if (type != null) {
        predicates.add(cb.equal(root.get("type"), type));
      }
      if (url != null) {
        // Performs a partial string match (contains)
        predicates.add(cb.like(root.get("url"), "%" + url + "%"));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
