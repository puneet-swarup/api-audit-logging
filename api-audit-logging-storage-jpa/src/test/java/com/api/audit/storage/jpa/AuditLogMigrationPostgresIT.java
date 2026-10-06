package com.api.audit.storage.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifies the library's PostgreSQL Flyway migrations against a real PostgreSQL instance.
 *
 * <p>This is the test that turns a claim into proof: the {@code V999}/{@code V1000}/{@code V1001}/
 * {@code V1002} scripts are executed against a real engine, and the resulting {@code api_audit_log}
 * schema is asserted. It is skipped automatically when no Docker runtime is available, so it never
 * breaks a developer machine that lacks containers.
 *
 * @author Puneet Swarup
 */
@EnabledIf("dockerAvailable")
class AuditLogMigrationPostgresIT {

  /** Condition method used by {@link EnabledIf} to skip when Docker is unavailable. */
  static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  void migrationsCreateTheAuditTableWithAllColumns() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("audit")
            .withUsername("audit")
            .withPassword("audit")) {
      postgres.start();

      DataSource dataSource =
          new DriverManagerDataSource(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/audit-migrations/postgresql")
          .load()
          .migrate();

      List<String> columns = columnNames(dataSource, "api_audit_log");
      assertThat(columns)
          .contains(
              "service_name",
              "type",
              "method",
              "url",
              "request_body",
              "response_body",
              "http_status",
              "duration",
              "correlation_id",
              "client_ip",
              "principal_name",
              "error_type",
              "error_message",
              "tags",
              "timestamp");
    }
  }

  private List<String> columnNames(DataSource dataSource, String table) throws Exception {
    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      try (ResultSet rs = meta.getColumns(null, null, table, null)) {
        while (rs.next()) {
          columns.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
        }
      }
    }
    return columns;
  }
}
