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
import org.testcontainers.containers.MySQLContainer;

/**
 * Verifies the library's MySQL Flyway migrations against a real MySQL instance.
 *
 * <p>Skipped automatically when no Docker runtime is available.
 *
 * @author Puneet Swarup
 */
@EnabledIf("dockerAvailable")
class AuditLogMigrationMysqlIT {

  /** Condition method used by {@link EnabledIf} to skip when Docker is unavailable. */
  static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  void migrationsCreateTheAuditTableWithTagsColumn() throws Exception {
    try (MySQLContainer<?> mysql =
        new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("audit")
            .withUsername("audit")
            .withPassword("audit")) {
      mysql.start();

      DataSource dataSource =
          new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/audit-migrations/mysql")
          .load()
          .migrate();

      List<String> columns = columnNames(dataSource, "api_audit_log");
      assertThat(columns).contains("service_name", "correlation_id", "tags", "timestamp");
    }
  }

  private List<String> columnNames(DataSource dataSource, String table) throws Exception {
    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, null)) {
        while (rs.next()) {
          columns.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
        }
      }
    }
    return columns;
  }
}
