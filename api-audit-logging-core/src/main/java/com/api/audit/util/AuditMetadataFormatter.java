package com.api.audit.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * Formats HTTP metadata for audit records without leaking common secrets.
 *
 * <p>Headers are stored as compact JSON strings so database, Kafka, and in-memory stores can all
 * carry the same shape without introducing a storage-specific metadata model. Sensitive headers are
 * redacted before the record is published.
 *
 * @author Puneet Swarup
 */
public final class AuditMetadataFormatter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String MASK = "******";

  private static final List<String> SENSITIVE_HEADER_TOKENS =
      List.of("authorization", "cookie", "token", "secret", "api-key", "apikey", "x-api-key");

  private AuditMetadataFormatter() {}

  /** Serializes servlet request headers as a redacted JSON object. */
  public static String requestHeaders(HttpServletRequest request, int maxHeaderSize) {
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    while (names != null && names.hasMoreElements()) {
      String name = names.nextElement();
      headers.put(name, requestHeaderValues(request, name));
    }
    return toJson(headers, maxHeaderSize);
  }

  /** Serializes servlet response headers as a redacted JSON object. */
  public static String responseHeaders(HttpServletResponse response, int maxHeaderSize) {
    return toJson(
        response.getHeaderNames().stream()
            .collect(
                LinkedHashMap::new,
                (map, name) -> map.put(name, response.getHeaders(name)),
                Map::putAll),
        maxHeaderSize);
  }

  /** Serializes Spring HTTP headers as a redacted JSON object. */
  public static String headers(HttpHeaders headers, int maxHeaderSize) {
    return toJson(headers, maxHeaderSize);
  }

  /** Serializes Feign-style headers as a redacted JSON object. */
  public static String headers(Map<String, Collection<String>> headers, int maxHeaderSize) {
    return toJson(headers == null ? Map.of() : headers, maxHeaderSize);
  }

  /** Resolves the best available client IP, respecting common forwarding headers. */
  public static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }

    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }

    return request.getRemoteAddr();
  }

  /** Returns the authenticated principal name when Spring/Security has made one available. */
  public static String principalName(HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    return principal == null ? null : principal.getName();
  }

  private static Collection<String> requestHeaderValues(HttpServletRequest request, String name) {
    return java.util.Collections.list(request.getHeaders(name));
  }

  private static String toJson(
      Map<String, ? extends Collection<String>> headers, int maxHeaderSize) {
    Map<String, Object> redacted = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> redacted.put(name, isSensitive(name) ? List.of(MASK) : values));

    try {
      String json = OBJECT_MAPPER.writeValueAsString(redacted);
      int bytes = json.getBytes(StandardCharsets.UTF_8).length;
      return bytes > maxHeaderSize ? "[HEADERS TOO LARGE: " + bytes + " bytes]" : json;
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private static boolean isSensitive(String headerName) {
    if (headerName == null) {
      return false;
    }
    String lower = headerName.toLowerCase(Locale.ROOT);
    return SENSITIVE_HEADER_TOKENS.stream().anyMatch(lower::contains);
  }
}
