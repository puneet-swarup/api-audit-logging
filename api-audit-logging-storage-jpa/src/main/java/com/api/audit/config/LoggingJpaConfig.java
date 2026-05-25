package com.api.audit.config;

import com.api.audit.repository.ApiAuditLogRepository;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import com.api.audit.storage.jpa.JpaAuditLogSearchStore;
import com.api.audit.storage.jpa.JpaAuditLogStore;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * JPA persistence configuration for the Audit Library.
 *
 * <p>This configuration is discovered through Spring Boot's auto-configuration imports when the JPA
 * storage module is on the classpath. The common audit auto-configuration is ordered after this
 * class, so the {@link AuditLogStore} and {@link AuditLogSearchStore} beans are available before
 * the listener and internal search endpoint are created.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Registers JPA repositories via {@code @EnableJpaRepositories}
 *   <li>Injects the {@code ApiAuditLog} entity into the host's persistence unit
 *   <li>Registers {@link JpaAuditLogStore} as the {@link AuditLogStore} implementation
 *   <li>Registers {@link JpaAuditLogSearchStore} as the {@link AuditLogSearchStore} impl
 *   <li>Optionally configures Flyway for schema migration
 * </ul>
 *
 * @author Puneet Swarup
 */
@AutoConfiguration
@EnableJpaRepositories(basePackages = "com.api.audit.repository")
@ConditionalOnExpression(
    "'${audit.logging.enabled:true}' == 'true' and '${audit.logging.storage.type:}' == 'jpa'")
@RequiredArgsConstructor
public class LoggingJpaConfig {

  private final AuditLoggingProperties properties;

  /**
   * Creates a {@link BeanPostProcessor} that injects the library's {@code ApiAuditLog} entity into
   * the host application's JPA persistence unit.
   *
   * <p>This solves the "Entity Scanning" problem for shared libraries — the host application's
   * {@code @EntityScan} would otherwise ignore library entities.
   */
  @Bean
  public static BeanPostProcessor persistenceUnitPostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LocalContainerEntityManagerFactoryBean factoryBean) {
          factoryBean.setPersistenceUnitPostProcessors(
              pui -> pui.addManagedClassName("com.api.audit.entity.ApiAuditLog"));
        }
        return bean;
      }
    };
  }

  /**
   * Registers the JPA implementation of {@link AuditLogStore}.
   *
   * <p>Maps incoming {@link com.api.audit.model.AuditLogRecord} objects to {@link
   * com.api.audit.entity.ApiAuditLog} JPA entities and persists them.
   *
   * <p>Skipped if a custom {@link AuditLogStore} bean is already present in the application context
   * (e.g. a Kafka or Elasticsearch implementation).
   */
  @Bean
  @ConditionalOnMissingBean(AuditLogStore.class)
  public AuditLogStore auditLogStore(ApiAuditLogRepository repository) {
    return new JpaAuditLogStore(repository);
  }

  /**
   * Registers the JPA implementation of {@link AuditLogSearchStore}.
   *
   * <p>Translates search parameters into JPA Specifications and returns results as {@link
   * com.api.audit.model.AuditLogRecord} objects (storage-agnostic).
   *
   * <p>Skipped if a custom {@link AuditLogSearchStore} bean is already present.
   */
  @Bean
  @ConditionalOnMissingBean(AuditLogSearchStore.class)
  public AuditLogSearchStore auditLogSearchStore(ApiAuditLogRepository repository) {
    return new JpaAuditLogSearchStore(repository);
  }

  /**
   * Adds the library's Flyway migration path when explicitly enabled.
   *
   * <p>Only activated when BOTH:
   *
   * <ol>
   *   <li>Flyway is on the classpath
   *   <li>{@code audit.logging.flyway.enabled=true}
   * </ol>
   *
   * <p>When disabled (default), the host must run the library's migration script manually: {@code
   * classpath:db/audit-migrations/{database}/V999__audit_log_init.sql}
   */
  @Bean
  @ConditionalOnClass(Flyway.class)
  @ConditionalOnProperty(
      prefix = "audit.logging.flyway",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public FlywayConfigurationCustomizer flywayCustomizer(DataSource dataSource) {
    return config ->
        config
            .locations("classpath:db/migration", AuditMigrationLocations.resolve(dataSource))
            .outOfOrder(true)
            .baselineOnMigrate(true);
  }
}
