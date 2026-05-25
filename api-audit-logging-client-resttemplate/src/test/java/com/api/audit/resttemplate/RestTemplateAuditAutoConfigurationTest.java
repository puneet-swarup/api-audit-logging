package com.api.audit.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unit tests for blocking HTTP client audit auto-configuration.
 *
 * @author Puneet Swarup
 */
class RestTemplateAuditAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RestTemplateAuditAutoConfiguration.class))
          .withUserConfiguration(TestPublisherConfig.class);

  @Test
  void registersRestTemplateAndRestClientCustomizers() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RestTemplateCustomizer.class);
          assertThat(context).hasSingleBean(RestClientCustomizer.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class TestPublisherConfig {

    @Bean
    ApplicationEventPublisher applicationEventPublisher() {
      return event -> {};
    }
  }
}
