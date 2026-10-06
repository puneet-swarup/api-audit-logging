package com.api.audit.storage.jdbc;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

  /** Columns that may be used for ordering; anything else falls back to {@code timestamp}. */
  private static final java.util.Set<String> SORTABLE_COLUMNS =
      java.util.Set.of(
          "timestamp",
          "duration",
          "http_status",
          "service_name",
          "type",
          "method",
          "correlation_id");

  private final JdbcTemplate jdbcTemplate;

  /** Persists one audit record using a simple insert statement. */
  @Override
  public void save(AuditLogRecord record) {
    jdbcTemplate.update(
        """
        INSERT INTO api_audit_log
        (schema_version, service_name, type, method, description, url, query_string, request_headers,
         response_headers, request_body, response_body, http_status, duration, correlation_id,
         client_ip, user_agent, principal_name, error_type, error_message, tags, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        record.getSchemaVersion(),
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
        serializeTags(record.getTags()),
        Timestamp.valueOf(record.getTimestamp()));
  }

  /** Searches records with optional filters, ordering, and database-level pagination. */
  @Override
  public AuditLogPage<AuditLogRecord> search(AuditLogQuery query) {
    QueryParts where = buildWhereClause(query);

    List<Object> pageArgs = new ArrayList<>(where.args());
    pageArgs.add(query.effectiveSize());
    pageArgs.add(query.offset());

    String orderBy = orderByClause(query);
    List<AuditLogRecord> content =
        jdbcTemplate.query(
            "SELECT * FROM api_audit_log " + where.whereClause() + orderBy + " LIMIT ? OFFSET ?",
            ROW_MAPPER,
            pageArgs.toArray());

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_audit_log " + where.whereClause(),
            Long.class,
            where.args().toArray());

    return new AuditLogPage<>(
        content, query.effectivePage(), query.effectiveSize(), total == null ? 0 : total);
  }

  private String orderByClause(AuditLogQuery query) {
    String column = query.getSortBy();
    if (column == null || !SORTABLE_COLUMNS.contains(column)) {
      column = "timestamp";
    }
    return " ORDER BY " + column + (query.isSortAscending() ? " ASC" : " DESC");
  }

  private QueryParts buildWhereClause(AuditLogQuery query) {
    List<String> clauses = new ArrayList<>();
    List<Object> args = new ArrayList<>();

    if (query.getCorrelationId() != null) {
      clauses.add("correlation_id = ?");
      args.add(query.getCorrelationId());
    }
    if (query.getStart() != null) {
      clauses.add("timestamp >= ?");
      args.add(Timestamp.valueOf(query.getStart()));
    }
    if (query.getEnd() != null) {
      clauses.add("timestamp <= ?");
      args.add(Timestamp.valueOf(query.getEnd()));
    }
    if (query.getType() != null) {
      clauses.add("type = ?");
      args.add(query.getType());
    }
    if (query.getUrl() != null) {
      clauses.add("url LIKE ?");
      args.add("%" + query.getUrl() + "%");
    }
    addEqualIfPresent(clauses, args, "service_name", query.getServiceName());
    addEqualIfPresent(clauses, args, "method", query.getMethod());
    if (query.getHttpStatus() != null) {
      clauses.add("http_status = ?");
      args.add(query.getHttpStatus());
    }
    addEqualIfPresent(clauses, args, "client_ip", query.getClientIp());
    addEqualIfPresent(clauses, args, "principal_name", query.getPrincipalName());
    addEqualIfPresent(clauses, args, "error_type", query.getErrorType());
    if (query.getTagKey() != null && !query.getTagKey().isBlank()) {
      if (query.getTagValue() == null) {
        clauses.add("tags LIKE ?");
        args.add("%\"" + query.getTagKey() + "\":%");
      } else {
        clauses.add("tags LIKE ?");
        args.add("%\"" + query.getTagKey() + "\":\"" + query.getTagValue() + "\"%");
      }
    }

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
        .schemaVersion(rs.getObject("schema_version") == null ? 1 : rs.getInt("schema_version"))
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
        .tags(parseTags(rs.getString("tags")))
        .build();
  }

  /**
   * Serializes the tag map to a compact JSON object string. Kept dependency-free because the JDBC
   * module deliberately avoids extra libraries.
   *
   * @param tags the tag map; may be {@code null} or empty
   * @return a JSON object string, or {@code null} when there are no tags
   */
  private static String serializeTags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(escape(entry.getKey())).append("\":\"");
      sb.append(escape(entry.getValue())).append('"');
    }
    return sb.append('}').toString();
  }

  /**
   * Parses the compact JSON object string written by {@link #serializeTags(Map)}. Malformed input
   * yields an empty map.
   *
   * @param json the stored JSON; may be {@code null}
   * @return a parsed map, never {@code null}
   */
  private static Map<String, String> parseTags(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    String trimmed = json.trim();
    if (trimmed.length() < 2 || trimmed.charAt(0) != '{') {
      return Map.of();
    }
    String inner = trimmed.substring(1, trimmed.length() - 1);
    if (inner.isBlank()) {
      return Map.of();
    }
    for (String pair : inner.split(",")) {
      int colon = pair.indexOf(':');
      if (colon < 0) {
        continue;
      }
      result.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
    }
    return result;
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String unquote(String value) {
    String v = value.trim();
    if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
      v = v.substring(1, v.length() - 1);
    }
    return v.replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private record QueryParts(String whereClause, List<Object> args) {}
}
