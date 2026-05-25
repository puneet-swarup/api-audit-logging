package com.api.audit.config;

import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;

/**
 * Resolves the Flyway migration location that matches the application's active database.
 *
 * <p>Audit table DDL is intentionally stored per database vendor. A single SQL file looks tempting,
 * but identity columns, large text types, and timestamp precision are not portable enough across
 * production databases. Keeping vendor scripts separate makes the generated schema predictable.
 *
 * @author Puneet Swarup
 */
public final class AuditMigrationLocations {

  private static final String BASE_LOCATION = "classpath:db/audit-migrations/";

  private AuditMigrationLocations() {}

  /** Returns the audit migration location for the JDBC URL exposed by the supplied datasource. */
  public static String resolve(DataSource dataSource) {
    String jdbcUrl = jdbcUrl(dataSource);

    if (jdbcUrl.contains(":postgresql:")) {
      return BASE_LOCATION + "postgresql";
    }
    if (jdbcUrl.contains(":mysql:") || jdbcUrl.contains(":mariadb:")) {
      return BASE_LOCATION + "mysql";
    }
    if (jdbcUrl.contains(":sqlserver:")) {
      return BASE_LOCATION + "sqlserver";
    }
    if (jdbcUrl.contains(":oracle:")) {
      return BASE_LOCATION + "oracle";
    }
    if (jdbcUrl.contains(":h2:")) {
      return BASE_LOCATION + "h2";
    }

    throw new IllegalStateException(
        "Unsupported database for API audit Flyway migration: "
            + jdbcUrl
            + ". Disable audit.logging.flyway.enabled and provide your own schema migration.");
  }

  private static String jdbcUrl(DataSource dataSource) {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getURL().toLowerCase(Locale.ROOT);
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to resolve database type for audit migrations.", ex);
    }
  }
}
