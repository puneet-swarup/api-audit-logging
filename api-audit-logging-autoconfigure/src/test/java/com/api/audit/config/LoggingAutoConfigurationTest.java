package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.api.audit.filter.IncomingLoggingFilter;
import com.api.audit.listener.ApiLogListener;
import com.api.audit.spi.AuditMetrics;
import com.api.audit.spi.NoOpAuditMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;

class LoggingAutoConfigurationTest {

  private final AuditLoggingProperties properties = new AuditLoggingProperties();
  private final LoggingAutoConfiguration config = new LoggingAutoConfiguration(properties);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MicrometerAuditMetricsAutoConfiguration.class,
                  AuditMetricsAutoConfiguration.class,
                  LoggingAutoConfiguration.class));

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(config, "appName", "test-service");
  }

  @Test
  @DisplayName("GIVEN unknown-service name WHEN printAuditBanner THEN no exception")
  void testVerifyLibraryInitialization_UnknownService() {
    ReflectionTestUtils.setField(config, "appName", "unknown-service");
    assertDoesNotThrow(config::printAuditBanner);
  }

  @Test
  @DisplayName("GIVEN known service name WHEN printAuditBanner THEN no exception")
  void testVerifyLibraryInitialization_KnownService() {
    ReflectionTestUtils.setField(config, "appName", "sp-policy-service");
    assertDoesNotThrow(config::printAuditBanner);
  }

  @Test
  @DisplayName("GIVEN loggingFilterRegistration WHEN initialized THEN it runs after security")
  void testLoggingFilterRegistration() {
    IncomingLoggingFilter mockFilter = mock(IncomingLoggingFilter.class);
    var bean = config.loggingFilterRegistration(mockFilter);
    assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, bean.getOrder());
  }

  @Test
  @DisplayName("GIVEN auditSecurityFilterRegistration WHEN initialized THEN it runs first")
  void testAuditSecurityFilterRegistration() {
    var bean = config.auditSecurityFilterRegistration();
    assertEquals(Ordered.HIGHEST_PRECEDENCE, bean.getOrder());
  }

  @Test
  @DisplayName(
      "GIVEN audit.logging.enabled=false WHEN context loads THEN no LoggingAutoConfiguration bean")
  void shouldDisableAuditingWhenPropertyIsFalse() {
    contextRunner
        .withPropertyValues("audit.logging.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LoggingAutoConfiguration.class);
              assertThat(context).doesNotHaveBean("logExecutor");
              assertThat(context).doesNotHaveBean(AuditMetrics.class);
            });
  }

  @Test
  @DisplayName(
      "GIVEN no AuditLogStore WHEN context loads THEN listener and search endpoint stay absent")
  void shouldStartSafelyWithoutStorageStore() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(LoggingAutoConfiguration.class);
          assertThat(context).doesNotHaveBean(ApiLogListener.class);
          assertThat(context).doesNotHaveBean(com.api.audit.controller.ApiLogController.class);
        });
  }

  @Test
  @DisplayName("GIVEN no MeterRegistry WHEN context loads THEN no-op audit metrics are registered")
  void shouldRegisterNoOpMetricsWhenMicrometerRegistryIsAbsent() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AuditMetrics.class);
          assertThat(context).hasSingleBean(NoOpAuditMetrics.class);
        });
  }

  @Test
  @DisplayName(
      "GIVEN MeterRegistry WHEN context loads THEN Micrometer audit metrics are registered")
  void shouldRegisterMicrometerMetricsWhenRegistryIsPresent() {
    contextRunner
        .withBean(SimpleMeterRegistry.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuditMetrics.class);
              assertThat(context).hasSingleBean(MicrometerAuditMetrics.class);
            });
  }
}
