package com.api.audit.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for formatting audit metadata safely.
 *
 * <p>These tests protect the most important production promise of metadata capture: useful headers
 * are retained, but common credential-bearing headers are redacted before anything reaches storage.
 *
 * @author Puneet Swarup
 */
class AuditMetadataFormatterTest {

  @Test
  void masksSensitiveHeadersAndKeepsUsefulHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    request.addHeader("Authorization", "Bearer secret-token");
    request.addHeader("X-Request-Source", "mobile");

    String headers = AuditMetadataFormatter.requestHeaders(request, 20_000);

    assertThat(headers).contains("\"Authorization\":[\"******\"]");
    assertThat(headers).contains("\"X-Request-Source\":[\"mobile\"]");
    assertThat(headers).doesNotContain("secret-token");
  }

  @Test
  void resolvesForwardedClientIpBeforeRemoteAddress() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");

    assertThat(AuditMetadataFormatter.clientIp(request)).isEqualTo("203.0.113.10");
  }

  @Test
  void returnsMarkerWhenSerializedHeadersExceedLimit() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    request.addHeader("X-Large-Header", "abcdefghijklmnopqrstuvwxyz");

    String headers = AuditMetadataFormatter.requestHeaders(request, 10);

    assertThat(headers).startsWith("[HEADERS TOO LARGE:");
  }
}
