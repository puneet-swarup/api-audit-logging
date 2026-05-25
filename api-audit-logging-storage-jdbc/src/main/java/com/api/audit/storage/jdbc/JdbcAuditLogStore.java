package com.api.audit.storage.jdbc;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC implementation of audit storage.
 *
 * <p>This store is for applications that want database-backed audit logs without bringing in JPA or
 * entity scanning. It uses a plain {@link JdbcTemplate}, maps directly to the shared {@code
 * api_audit_log} table, and still participates in the same masking and async pipeline as the JPA
 * store.
 *
 * @author Puneet Swarup
 */
@RequiredArgsConstructor
public class JdbcAuditLogStore implements AuditLogStore, AuditLogSearchStore {

  private static final RowMapper<AuditLogRecord> ROW_MAPPER = JdbcAuditLogStore::toRecord;

  private final JdbcTemplate jdbcTemplate;

  /** Persists one audit record using a simple insert statement. */
  @Override
  public void save(AuditLogRecord record) {
    jdbcTemplate.update(
        """
        INSERT INTO api_audit_log
        (service_name, type, method, description, url, query_string, request_headers,
         response_headers, request_body, response_body, http_status, duration, correlation_id,
         client_ip, user_agent, principal_name, error_type, error_message, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        record.getServiceName(),
        record.getType(),
        record.getMethod(),
        record.getDescription(),
        record.getUrl(),
        record.getQueryString(),
        record.getRequestHeaders(),
        record.getResponseHeaders(),
        record.getRequestBody(),
        record.getResponseBody(),
        record.getHttpStatus(),
        record.getDuration(),
        record.getCorrelationId(),
        record.getClientIp(),
        record.getUserAgent(),
        record.getPrincipalName(),
        record.getErrorType(),
        record.getErrorMessage(),
        Timestamp.valueOf(record.getTimestamp()));
  }

  /** Searches records with optional filters and database-level pagination. */
  @Override
  public Page<AuditLogRecord> search(
      String correlationId,
      LocalDateTime start,
      LocalDateTime end,
      String type,
      String url,
      String serviceName,
      String method,
      Integer httpStatus,
      String clientIp,
      String principalName,
      String errorType,
      Pageable pageable) {

    QueryParts query =
        buildWhereClause(
            correlationId,
            start,
            end,
            type,
            url,
            serviceName,
            method,
            httpStatus,
            clientIp,
            principalName,
            errorType);
    List<Object> pageArgs = new ArrayList<>(query.args());
    pageArgs.add(pageable.getPageSize());
    pageArgs.add(pageable.getOffset());

    List<AuditLogRecord> content =
        jdbcTemplate.query(
            "SELECT * FROM api_audit_log "
                + query.whereClause()
                + " ORDER BY timestamp LIMIT ? OFFSET ?",
            ROW_MAPPER,
            pageArgs.toArray());

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_audit_log " + query.whereClause(),
            Long.class,
            query.args().toArray());

    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }

  private QueryParts buildWhereClause(
      String correlationId,
      LocalDateTime start,
      LocalDateTime end,
      String type,
      String url,
      String serviceName,
      String method,
      Integer httpStatus,
      String clientIp,
      String principalName,
      String errorType) {
    List<String> clauses = new ArrayList<>();
    List<Object> args = new ArrayList<>();

    if (correlationId != null) {
      clauses.add("correlation_id = ?");
      args.add(correlationId);
    }
    if (start != null) {
      clauses.add("timestamp >= ?");
      args.add(Timestamp.valueOf(start));
    }
    if (end != null) {
      clauses.add("timestamp <= ?");
      args.add(Timestamp.valueOf(end));
    }
    if (type != null) {
      clauses.add("type = ?");
      args.add(type);
    }
    if (url != null) {
      clauses.add("url LIKE ?");
      args.add("%" + url + "%");
    }
    addEqualIfPresent(clauses, args, "service_name", serviceName);
    addEqualIfPresent(clauses, args, "method", method);
    if (httpStatus != null) {
      clauses.add("http_status = ?");
      args.add(httpStatus);
    }
    addEqualIfPresent(clauses, args, "client_ip", clientIp);
    addEqualIfPresent(clauses, args, "principal_name", principalName);
    addEqualIfPresent(clauses, args, "error_type", errorType);

    return new QueryParts(clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses), args);
  }

  private void addEqualIfPresent(
      List<String> clauses, List<Object> args, String column, String value) {
    if (value != null) {
      clauses.add(column + " = ?");
      args.add(value);
    }
  }

  private static AuditLogRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
    return AuditLogRecord.builder()
        .serviceName(rs.getString("service_name"))
        .type(rs.getString("type"))
        .method(rs.getString("method"))
        .description(rs.getString("description"))
        .url(rs.getString("url"))
        .queryString(rs.getString("query_string"))
        .requestHeaders(rs.getString("request_headers"))
        .responseHeaders(rs.getString("response_headers"))
        .requestBody(rs.getString("request_body"))
        .responseBody(rs.getString("response_body"))
        .httpStatus((Integer) rs.getObject("http_status"))
        .duration(rs.getLong("duration"))
        .correlationId(rs.getString("correlation_id"))
        .clientIp(rs.getString("client_ip"))
        .userAgent(rs.getString("user_agent"))
        .principalName(rs.getString("principal_name"))
        .errorType(rs.getString("error_type"))
        .errorMessage(rs.getString("error_message"))
        .timestamp(rs.getTimestamp("timestamp").toLocalDateTime())
        .build();
  }

  private record QueryParts(String whereClause, List<Object> args) {}
}
