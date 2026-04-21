package com.api.audit.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.api.audit.config.AuditLoggingProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuditLogSecurityFilterTest {

  private AuditLoggingProperties properties;
  private AuditLogSecurityFilter filter;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    properties = new AuditLoggingProperties();
    filter = new AuditLogSecurityFilter(properties);
    chain = mock(FilterChain.class);
  }

  @Test
  @DisplayName("Non-audit paths pass through without any key check")
  void nonAuditPath_passesThrough() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/hello");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    verify(chain).doFilter(req, res);
    assertThat(res.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("Audit path blocked with 403 when no API key is configured")
  void auditPath_noKeyConfigured_returns403() throws Exception {
    properties.getInternal().setApiKey(null);
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/audit-logs");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(403);
    verifyNoInteractions(chain);
  }

  @Test
  @DisplayName("Audit path blocked with 401 when wrong key is provided")
  void auditPath_wrongKey_returns401() throws Exception {
    properties.getInternal().setApiKey("correct-key");
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/audit-logs");
    req.addHeader(AuditLogSecurityFilter.API_KEY_HEADER, "wrong-key");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }

  @Test
  @DisplayName("Audit path allowed with 200 when correct key is provided")
  void auditPath_correctKey_passes() throws Exception {
    properties.getInternal().setApiKey("my-secret");
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/audit-logs");
    req.addHeader(AuditLogSecurityFilter.API_KEY_HEADER, "my-secret");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    verify(chain).doFilter(req, res);
  }
}
