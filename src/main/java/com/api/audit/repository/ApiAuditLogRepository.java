package com.api.audit.repository;

import com.api.audit.entity.ApiAuditLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access interface for {@link ApiAuditLog} entities.
 *
 * <p>This repository leverages Spring Data JPA to provide standard abstraction over the underlying
 * audit table. It extends {@link JpaSpecificationExecutor} to facilitate the dynamic,
 * multi-parameter searching required by the {@code ApiLogSearchService}.
 *
 * <p>Operational usage typically includes:
 *
 * <ul>
 *   <li>Asynchronous insertion of new logs via the event listener.
 *   <li>Criteria-based querying for the administrative dashboard.
 *   <li>Retention-based cleanup of historical data.
 * </ul>
 *
 * @author Puneet Swarup
 * @see ApiAuditLog
 * @see org.springframework.data.jpa.domain.Specification
 */
@Repository
public interface ApiAuditLogRepository
    extends JpaRepository<ApiAuditLog, Long>, JpaSpecificationExecutor<ApiAuditLog> {

  /**
   * Deletes all audit log records that were created before the specified cutoff date.
   *
   * <p>This method is intended for use in scheduled maintenance tasks to enforce data retention
   * policies and prevent the audit table from growing indefinitely.
   *
   * @param cutoff the point in time before which all logs will be removed
   * @implNote This is a derived delete query. For large datasets, ensure this is called within a
   *     transactional context and monitor performance.
   */
  void deleteByTimestampBefore(LocalDateTime cutoff);
}
