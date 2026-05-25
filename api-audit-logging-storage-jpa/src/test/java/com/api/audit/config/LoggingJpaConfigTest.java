package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit tests for JPA storage activation rules.
 *
 * @author Puneet Swarup
 */
class LoggingJpaConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LoggingJpaConfig.class));

  @Test
  void doesNotActivateJpaStorageWhenStorageTypeIsMissing() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(LoggingJpaConfig.class));
  }
}
