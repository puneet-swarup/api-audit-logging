package com.api.audit.spi;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;

/**
 * Service Provider Interface for querying persisted audit logs.
 *
 * <p>Implementations translate an {@link AuditLogQuery} into their backend-specific query mechanism
 * (JPA Specification, SQL WHERE clause, in-memory filter, etc.) and return a framework-neutral
 * {@link AuditLogPage}.
 *
 * <p><b>Framework-neutral by design.</b> This SPI does not reference Spring Data or any other
 * framework. A custom store (Elasticsearch, a remote API, a data warehouse client) can implement it
 * without pulling Spring Data onto its classpath. The HTTP pagination concerns of the built-in
 * internal endpoint are handled at the web layer, not here.
 *
 * <p>The JPA implementation is provided by {@code api-audit-logging-storage-jpa}.
 *
 * @author Puneet Swarup
 * @see AuditLogQuery
 * @see AuditLogPage
 */
public interface AuditLogSearchStore {

  /**
   * Executes a paginated search based on the provided criteria.
   *
   * @param query the search criteria, pagination, and sorting; never {@code null}
   * @return a page of matching records; empty page if none match; never {@code null}
   */
  AuditLogPage<AuditLogRecord> search(AuditLogQuery query);
}
