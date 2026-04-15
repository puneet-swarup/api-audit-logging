package com.api.audit.config;

import lombok.NoArgsConstructor;
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
@NoArgsConstructor
@ConditionalOnProperty(prefix = "audit.logging", name = "enabled", havingValue = "true")
public class LoggingJpaConfig {

  /**
   * Creates a {@link BeanPostProcessor} that intercepts {@link
   * LocalContainerEntityManagerFactoryBean} initialization to inject library-specific entities.
   *
   * <p>This approach solves the common "Entity Scanning" issue in shared libraries where the host
   * application's {@code @EntityScan} would otherwise overwrite or ignore the library's
   * audit-migrations entities.
   *
   * @return a processor that adds {@code com.chola.entity.audit.ApiAuditLog} to the current
   *     persistence unit
   * @see
   *     org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo#addManagedClassName(String)
   */
  @Bean
  public static BeanPostProcessor persistenceUnitPostProcessor() {
    return new BeanPostProcessor() {
      /**
       * Intercepts the Entity Manager Factory bean before it is fully initialized.
       *
       * @param bean the bean instance
       * @param beanName the name of the bean
       * @return the potentially modified bean
       */
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LocalContainerEntityManagerFactoryBean factoryBean) {
          /*
           * Programmatically adds the library's audit entity to the host's
           * persistence unit info. This ensures the entity is managed by
           * Hibernate/JPA regardless of the host's scan settings.
           */
          factoryBean.setPersistenceUnitPostProcessors(
              pui -> pui.addManagedClassName("com.api.audit.entity.ApiAuditLog"));
        }
        return bean;
      }
    };
  }

  /**
   * Customizes Flyway migration locations to include library-audit-migrations migrations.
   *
   * @return a customizer that appends the audit-migrations migration path
   */
  @Bean
  @ConditionalOnClass(Flyway.class)
  public FlywayConfigurationCustomizer flywayCustomizer() {
    return config ->
        config
            .locations("classpath:db/migration", "classpath:db/audit-migrations")
            .outOfOrder(true)
            .baselineOnMigrate(true);
  }
}
