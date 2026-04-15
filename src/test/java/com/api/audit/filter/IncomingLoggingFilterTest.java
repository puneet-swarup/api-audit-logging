package com.api.audit.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class IncomingLoggingFilterTest {

  @Mock private ApplicationEventPublisher publisher;
  @Mock private FilterChain chain;
  private IncomingLoggingFilter filter;

  private final String appName = "TestApp";

  @BeforeEach
  void setUp() {
    filter = new IncomingLoggingFilter(publisher, appName);
    MDC.clear();
  }

  @Test
  @DisplayName(
      "GIVEN correlation ID header provided WHEN request processed THEN use provided CID and clear after")
  void testDoFilterInternal_WithCid() throws ServletException, IOException {
    // ARRANGE
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationContext.CORRELATION_ID_HEADER, "FIXED-CID-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // We must verify MDC *inside* the filter chain because it is cleared in finally
    doAnswer(
            invocation -> {
              // ASSERT: Check MDC while the filter is still executing the chain
              assertEquals("FIXED-CID-123", MDC.get(CorrelationContext.CORRELATION_ID_HEADER));
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    // ACT
    filter.doFilterInternal(request, response, chain);

    // ASSERT: Verify MDC is cleared after the filter finishes
    assertNull(
        MDC.get(CorrelationContext.CORRELATION_ID_HEADER), "MDC should be cleared after filter");

    // Verify that logic for audit event publishing was also covered
    // (If you set AUDIT_LOG_ENABLED as discussed in previous tests)
  }

  @Test
  @DisplayName("GIVEN NO correlation ID header WHEN request processed THEN generate UUID CID")
  void testDoFilterInternal_NoCid() throws ServletException, IOException {
    // ARRANGE
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Explicitly match the wrappers created inside the filter
    doAnswer(
            invocation -> {
              String generatedCid = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);

              // ASSERT inside the lambda
              assertNotNull(generatedCid, "MDC Correlation ID should have been generated");
              assertTrue(generatedCid.length() > 30, "Should be a valid UUID string");
              return null;
            })
        .when(chain)
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

    // ACT
    filter.doFilterInternal(request, response, chain);

    // ASSERT after filter
    assertNull(
        MDC.get(CorrelationContext.CORRELATION_ID_HEADER), "MDC must be cleared in finally block");
  }

  @Test
  @DisplayName(
      "GIVEN AUDIT_LOG_ENABLED attribute is null WHEN request processed THEN do not publish event (Branch Coverage)")
  void testDoFilterInternal_AuditDisabled() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    // Do NOT set request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chain);

    verify(publisher, never()).publishEvent(any(ApiLogEvent.class));
    assertNull(MDC.get(CorrelationContext.CORRELATION_ID_HEADER));
  }

  @Test
  @DisplayName(
      "GIVEN Audit Enabled WHEN filter runs THEN verify correlationId is populated in the event")
  void testCorrelationIdPopulation() throws ServletException, IOException {
    // ARRANGE
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationContext.CORRELATION_ID_HEADER, "TEST-CID-123");
    request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Capture the event
    ArgumentCaptor<ApiLogEvent> eventCaptor = ArgumentCaptor.forClass(ApiLogEvent.class);

    // ACT
    filter.doFilterInternal(request, response, chain);

    // ASSERT
    verify(publisher).publishEvent(eventCaptor.capture());
    ApiLogEvent capturedEvent = eventCaptor.getValue();

    // Java Record accessor usage: capturedEvent.log()
    assertNotNull(capturedEvent.log());
    assertEquals("TEST-CID-123", capturedEvent.log().getCorrelationId());
  }
}
