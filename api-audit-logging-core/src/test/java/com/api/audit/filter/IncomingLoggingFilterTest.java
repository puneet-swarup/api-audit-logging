package com.api.audit.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.audit.config.AuditLoggingProperties;
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
    filter = new IncomingLoggingFilter(publisher, appName, new AuditLoggingProperties());
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
    assertNotNull(capturedEvent.record());
    assertEquals("TEST-CID-123", capturedEvent.record().getCorrelationId());
  }

  @Test
  @DisplayName("GIVEN body exceeds configured limit WHEN audited THEN stores truncation marker")
  void bodyLimitIsConfigurable() throws ServletException, IOException {
    AuditLoggingProperties properties = new AuditLoggingProperties();
    properties.getCapture().setMaxBodySize(5);
    filter = new IncomingLoggingFilter(publisher, appName, properties);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/limited");
    request.setContentType("application/json");
    request.setContent(
        "{\"message\":\"too-large\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    ArgumentCaptor<ApiLogEvent> eventCaptor = ArgumentCaptor.forClass(ApiLogEvent.class);

    filter.doFilterInternal(request, response, chain);

    verify(publisher).publishEvent(eventCaptor.capture());
    assertTrue(eventCaptor.getValue().record().getRequestBody().startsWith("[REQUEST TOO LARGE:"));
  }

  @Test
  @DisplayName("GIVEN path is excluded WHEN filter runs THEN request passes without audit capture")
  void excludedPathSkipsInboundAuditCapture() throws ServletException, IOException {
    AuditLoggingProperties properties = new AuditLoggingProperties();
    properties.getCapture().getExcludedPaths().add("/actuator/**");
    filter = new IncomingLoggingFilter(publisher, appName, properties);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(publisher, never()).publishEvent(any(ApiLogEvent.class));
  }

  @Test
  @DisplayName(
      "GIVEN path is outside include list WHEN filter runs THEN request passes without audit capture")
  void nonIncludedPathSkipsInboundAuditCapture() throws ServletException, IOException {
    AuditLoggingProperties properties = new AuditLoggingProperties();
    properties
        .getCapture()
        .setIncludedPaths(new java.util.ArrayList<>(java.util.List.of("/api/**")));
    filter = new IncomingLoggingFilter(publisher, appName, properties);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/status");
    request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(publisher, never()).publishEvent(any(ApiLogEvent.class));
  }

  @Test
  @DisplayName("GIVEN audited request fails WHEN filter exits THEN publishes error metadata")
  void capturesErrorMetadataWhenRequestFails() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fail");
    request.setAttribute("AUDIT_LOG_ENABLED", true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    ArgumentCaptor<ApiLogEvent> eventCaptor = ArgumentCaptor.forClass(ApiLogEvent.class);
    doThrow(new ServletException("Controller failed")).when(chain).doFilter(any(), any());

    assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));

    verify(publisher).publishEvent(eventCaptor.capture());
    assertEquals("INCOMING_ERROR", eventCaptor.getValue().record().getType());
    assertEquals(ServletException.class.getName(), eventCaptor.getValue().record().getErrorType());
    assertEquals("Controller failed", eventCaptor.getValue().record().getErrorMessage());
  }
}
