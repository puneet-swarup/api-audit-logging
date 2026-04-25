package com.api.audit.config;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * Persistence configuration for the Audit Library.
 *
 * <p>This configuration handles the registration of library-specific JPA repositories and ensures
 * that the audit-migrations {@code ApiAuditLog} entity is dynamically mapped into the host
 * application's {@link jakarta.persistence.EntityManager}.
 *
 * <p>By using a {@link BeanPostProcessor}, this library can inject its managed entities into an
 * existing persistence unit without requiring the host application to explicitly scan the library's
 * entity packages.
 *
 * @author Puneet Swarup
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.api.audit.repository")
@ConditionalOnProperty(prefix = "audit.logging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LoggingJpaConfig {

  private final AuditLoggingProperties properties;

  /**
   * Creates a {@link BeanPostProcessor} that intercepts {@link
   * LocalContainerEntityManagerFactoryBean} initialization to inject library-specific entities.
   *
   * <p>This approach solves the common "Entity Scanning" issue in shared libraries where the host
   * application's {@code @EntityScan} would otherwise overwrite or ignore the library's
   * audit-migrations entities.
   *
   * @return a processor that adds {@code com.api.audit.entity.ApiAuditLog} to the current
   *     persistence unit
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
   * Customizes Flyway migration locations to include the library's audit schema migration.
   *
   * <p>Only activated when BOTH:
   *
   * <ol>
   *   <li>Flyway is on the classpath ({@code @ConditionalOnClass})
   *   <li>{@code audit.logging.flyway.enabled=true} is set explicitly
   * </ol>
   *
   * <p>Default behaviour is OFF. To enable, add to your {@code application.yml}:
   *
   * <pre>{@code
   * audit:
   *   logging:
   *     flyway:
   *       enabled: true
   * }</pre>
   *
   * <p>If disabled, host must apply the SQL script manually from: {@code
   * classpath:db/audit-migrations/V999__audit_log_init.sql}
   *
   * @return a customizer that appends the audit-migrations migration path
   */
  @Bean
  @ConditionalOnClass(Flyway.class)
  @ConditionalOnProperty(
      prefix = "audit.logging.flyway",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public FlywayConfigurationCustomizer flywayCustomizer() {
    return config ->
        config
            .locations("classpath:db/migration", "classpath:db/audit-migrations")
            .outOfOrder(true)
            .baselineOnMigrate(true);
  }
}
