package com.api.audit.util;

import java.util.Arrays;
import java.util.List;

/**
 * Utility for redacting sensitive information from JSON strings before persistence or display.
 *
 * <p>This class provides a rule-based masking mechanism to ensure that Personally Identifiable
 * Information (PII) and Sensitive Personal Information (SPI), such as credentials and payment
 * details, are not stored in plain text within the audit logs.
 *
 * <p>The masking process uses case-insensitive regular expressions to identify key-value pairs and
 * replace their values with a masked literal ({@code ******}) while preserving the structural
 * integrity of the JSON.
 *
 * @author Puneet Swarup
 */
public class JsonMasker {

  /** Private constructor to prevent instantiation of this utility class. */
  private JsonMasker() {
    // Utility class pattern
  }

  /**
   * The collection of JSON keys identified as sensitive.
   *
   * <p>Currently tracked keys include: {@code password}, {@code token}, {@code cvv}, {@code
   * cardNumber}, {@code secret}, and {@code authorization}.
   */
  private static final List<String> SENSITIVE_KEYS =
      Arrays.asList("password", "token", "cvv", "cardNumber", "secret", "authorization");

  /**
   * The regex pattern used to locate sensitive fields.
   *
   * <p>Pattern details:
   *
   * <ul>
   *   <li>{@code (?i)}: Case-insensitive matching.
   *   <li>{@code "(%s)"}: Captures the specific key name.
   *   <li>{@code \s*:\s*}: Handles variable spacing around the colon separator.
   *   <li>{@code "?([^,\"}]+)"?}: Captures the value regardless of whether it is quoted (string) or
   *       unquoted (numeric/boolean).
   * </ul>
   */
  private static final String REGEX_PATTERN = "(?i)\"(%s)\"\\s*:\\s*\"?([^,\"}]+)\"?";

  /**
   * Sanitizes a JSON string by masking values associated with sensitive keys.
   *
   * <p>Example transformation:
   *
   * <pre>{@code
   * Input:  {"password": "mySecret123", "id": 101}
   * Output: {"password": "******", "id": 101}
   * }</pre>
   *
   * @param json the raw JSON string to be processed
   * @return the sanitized JSON string, or {@code null} if the input was null
   * @implNote For 2026 performance optimization, this method performs multiple passes over the
   *     string. For extremely large JSON documents, consider a stream-based parsing approach to
   *     reduce memory pressure.
   */
  public static String mask(String json) {
    if (json == null) return null;

    String maskedJson = json;
    for (String key : SENSITIVE_KEYS) {
      // Replaces the value group ($2) with the mask while keeping the key ($1)
      maskedJson = maskedJson.replaceAll(String.format(REGEX_PATTERN, key), "\"$1\":\"******\"");
    }
    return maskedJson;
  }
}
