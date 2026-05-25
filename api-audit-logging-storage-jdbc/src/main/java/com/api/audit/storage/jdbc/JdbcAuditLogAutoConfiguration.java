package com.api.audit.storage.jdbc;

import com.api.audit.config.AuditMigrationLocations;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for JDBC-backed audit storage.
 *
 * <p>This module is the lightweight database option: it needs a {@link JdbcTemplate} and the audit
 * table, but it does not require JPA repositories or entity registration.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnExpression(
    "'${audit.logging.enabled:true}' == 'true' and '${audit.logging.storage.type:}' == 'jdbc'")
public class JdbcAuditLogAutoConfiguration {

  /** Registers the JDBC store when no other sink has been supplied. */
  @Bean
  @ConditionalOnMissingBean(AuditLogStore.class)
  public JdbcAuditLogStore jdbcAuditLogStore(JdbcTemplate jdbcTemplate) {
    return new JdbcAuditLogStore(jdbcTemplate);
  }

  /** Exposes JDBC-backed search for the internal audit endpoint. */
  @Bean
  @ConditionalOnMissingBean(AuditLogSearchStore.class)
  public AuditLogSearchStore jdbcAuditLogSearchStore(JdbcAuditLogStore store) {
    return store;
  }

  /** Adds the vendor-specific audit migration path when Flyway schema management is enabled. */
  @Bean
  @ConditionalOnClass(Flyway.class)
  @ConditionalOnProperty(
      prefix = "audit.logging.flyway",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public FlywayConfigurationCustomizer jdbcAuditFlywayCustomizer(DataSource dataSource) {
    return config ->
        config
            .locations("classpath:db/migration", AuditMigrationLocations.resolve(dataSource))
            .outOfOrder(true)
            .baselineOnMigrate(true);
  }
}
