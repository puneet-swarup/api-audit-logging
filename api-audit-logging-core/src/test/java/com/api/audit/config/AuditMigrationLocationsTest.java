package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for resolving vendor-specific audit schema migration locations.
 *
 * <p>This is deliberately tested without real database connections. The resolver only needs the
 * JDBC URL, and mocking the metadata keeps the test fast while still protecting the public
 * configuration behavior.
 *
 * @author Puneet Swarup
 */
class AuditMigrationLocationsTest {

  @Test
  void resolvesSupportedDatabaseLocationsFromJdbcUrl() throws Exception {
    assertThat(AuditMigrationLocations.resolve(dataSource("jdbc:h2:mem:testdb")))
        .isEqualTo("classpath:db/audit-migrations/h2");
    assertThat(AuditMigrationLocations.resolve(dataSource("jdbc:postgresql://localhost/audit")))
        .isEqualTo("classpath:db/audit-migrations/postgresql");
    assertThat(AuditMigrationLocations.resolve(dataSource("jdbc:mysql://localhost/audit")))
        .isEqualTo("classpath:db/audit-migrations/mysql");
    assertThat(AuditMigrationLocations.resolve(dataSource("jdbc:mariadb://localhost/audit")))
        .isEqualTo("classpath:db/audit-migrations/mysql");
    assertThat(AuditMigrationLocations.resolve(dataSource("jdbc:sqlserver://localhost")))
        .isEqualTo("classpath:db/audit-migrations/sqlserver");
    assertThat(
            AuditMigrationLocations.resolve(dataSource("jdbc:oracle:thin:@localhost:1521/XEPDB1")))
        .isEqualTo("classpath:db/audit-migrations/oracle");
  }

  @Test
  void failsClearlyForUnsupportedDatabase() throws Exception {
    assertThatThrownBy(() -> AuditMigrationLocations.resolve(dataSource("jdbc:unknown://host/db")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported database");
  }

  private DataSource dataSource(String jdbcUrl) throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(jdbcUrl);

    return dataSource;
  }
}
