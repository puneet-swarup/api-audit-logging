package com.api.audit.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Unit tests for the starter-owned default storage selection.
 *
 * @author Puneet Swarup
 */
class AuditLoggingStarterEnvironmentPostProcessorTest {

  private final AuditLoggingStarterEnvironmentPostProcessor postProcessor =
      new AuditLoggingStarterEnvironmentPostProcessor();

  @Test
  void defaultsStorageToJpaWhenHostHasNotSelectedStorage() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("audit.logging.storage.type")).isEqualTo("jpa");
  }

  @Test
  void keepsExplicitHostStorageSelection() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", java.util.Map.of("audit.logging.storage.type", "jdbc")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("audit.logging.storage.type")).isEqualTo("jdbc");
  }
}
