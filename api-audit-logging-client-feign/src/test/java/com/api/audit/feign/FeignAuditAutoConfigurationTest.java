package com.api.audit.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.api.audit.context.CorrelationContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for Feign audit auto-configuration behavior.
 *
 * @author Puneet Swarup
 */
class FeignAuditAutoConfigurationTest {

  private final FeignAuditAutoConfiguration config = new FeignAuditAutoConfiguration();

  /** Verifies that the same correlation ID assigned to an inbound request follows Feign calls. */
  @Test
  void correlationInterceptorAddsCorrelationIdWhenMdcContainsIt() {
    var interceptor = config.correlationInterceptor();
    RequestTemplate template = new RequestTemplate();
    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "MDC-CID-123");

    interceptor.apply(template);

    assertTrue(template.headers().containsKey(CorrelationContext.CORRELATION_ID_HEADER));
    assertEquals(
        "MDC-CID-123",
        template.headers().get(CorrelationContext.CORRELATION_ID_HEADER).iterator().next());
    MDC.clear();
  }

  /** Verifies that the interceptor stays quiet when no request correlation context exists. */
  @Test
  void correlationInterceptorDoesNotAddHeaderWhenMdcDoesNotContainCorrelationId() {
    MDC.remove(CorrelationContext.CORRELATION_ID_HEADER);
    var interceptor = config.correlationInterceptor();
    RequestTemplate template = new RequestTemplate();

    interceptor.apply(template);

    assertFalse(template.headers().containsKey(CorrelationContext.CORRELATION_ID_HEADER));
  }
}
