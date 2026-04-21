package com.api.audit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.api.audit.context.CorrelationContext;
import com.api.audit.filter.IncomingLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;

class LoggingAutoConfigurationTest {
  private final AuditLoggingProperties properties = new AuditLoggingProperties();
  private final LoggingAutoConfiguration config = new LoggingAutoConfiguration(properties);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

  @BeforeEach
  void setUp() {
    // Reset appName to a known value before each test that needs it
    ReflectionTestUtils.setField(config, "appName", "test-service");
  }

  @Test
  @DisplayName(
      "GIVEN no spring.application.name property WHEN printAuditBanner THEN log warning (Branch Coverage)")
  void testVerifyLibraryInitialization_UnknownService() {
    ReflectionTestUtils.setField(config, "appName", "unknown-service");
    assertDoesNotThrow(config::printAuditBanner);
  }

  @Test
  @DisplayName(
      "GIVEN spring.application.name property WHEN printAuditBanner THEN log success (Branch Coverage)")
  void testVerifyLibraryInitialization_KnownService() {
    ReflectionTestUtils.setField(config, "appName", "sp-policy-service");
    assertDoesNotThrow(config::printAuditBanner);
  }

  @Test
  @DisplayName(
      "GIVEN loggingFilterRegistration WHEN initialized THEN verify order is highest precedence")
  void testLoggingFilterRegistration() {
    IncomingLoggingFilter mockFilter = mock(IncomingLoggingFilter.class);
    var bean = config.loggingFilterRegistration(mockFilter);
    assertEquals(Ordered.HIGHEST_PRECEDENCE, bean.getOrder());
  }

  @Test
  @DisplayName(
      "GIVEN correlationInterceptor WHEN intercepting request THEN verify MDC header logic")
  void testCorrelationInterceptor() {
    var interceptor = config.correlationInterceptor();

    feign.RequestTemplate templateWithCid = new feign.RequestTemplate();
    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "MDC-CID-123");

    interceptor.apply(templateWithCid);

    assertTrue(templateWithCid.headers().containsKey(CorrelationContext.CORRELATION_ID_HEADER));
    assertEquals(
        "MDC-CID-123",
        templateWithCid.headers().get(CorrelationContext.CORRELATION_ID_HEADER).iterator().next());

    feign.RequestTemplate templateWithoutCid = new feign.RequestTemplate();
    MDC.remove(CorrelationContext.CORRELATION_ID_HEADER);

    interceptor.apply(templateWithoutCid);

    assertFalse(
        templateWithoutCid.headers().containsKey(CorrelationContext.CORRELATION_ID_HEADER),
        "Header should not be present when MDC is empty");
  }

  @Test
  @DisplayName("Should disable auditing when property is explicitly false")
  void shouldDisableAuditingWhenPropertyIsFalse() {
    contextRunner
        .withPropertyValues("audit.logging.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LoggingAutoConfiguration.class);
              assertThat(context).doesNotHaveBean("logExecutor");
            });
  }
}
