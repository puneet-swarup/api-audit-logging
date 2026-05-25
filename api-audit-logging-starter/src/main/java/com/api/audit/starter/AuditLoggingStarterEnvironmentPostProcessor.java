package com.api.audit.starter;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Applies the opinionated starter default for audit storage.
 *
 * <p>Storage modules are explicit opt-in when used directly. The starter is the exception: because
 * it is the convenience dependency, it defaults to JPA storage unless the host application has
 * already selected another storage type.
 *
 * @author Puneet Swarup
 */
public class AuditLoggingStarterEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  private static final String PROPERTY_NAME = "audit.logging.storage.type";
  private static final String PROPERTY_SOURCE_NAME = "apiAuditLoggingStarterDefaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (StringUtils.hasText(environment.getProperty(PROPERTY_NAME))) {
      return;
    }
    environment
        .getPropertySources()
        .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(PROPERTY_NAME, "jpa")));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
