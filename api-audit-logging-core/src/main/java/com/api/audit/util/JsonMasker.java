package com.api.audit.util;

import com.api.audit.config.AuditLoggingProperties;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spring component that redacts sensitive information from JSON strings before persistence.
 *
 * <p>Two layers of masking are applied:
 *
 * <ol>
 *   <li><b>Built-in defaults</b> — always applied, regardless of configuration: {@code password,
 *       token, cvv, cardNumber, secret, authorization}.
 *   <li><b>Consumer-configured</b> — additional fields specified via {@code
 *       audit.logging.masking.additional-fields}.
 * </ol>
 *
 * <p>Matching is case-insensitive and uses a contains-check on the JSON key name.
 *
 * <p>Example transformation:
 *
 * <pre>{@code
 * Input:  {"password": "mySecret123", "otp": "9876", "id": 101}
 * Output: {"password": "******", "otp": "******", "id": 101}
 * }</pre>
 *
 * @author Puneet Swarup
 */
@Slf4j
@Component
public class JsonMasker {

  private static final String MASK = "\"******\"";
  private static final String REGEX_PATTERN = "(?i)\"(%s)\"\\s*:\\s*\"?([^,\"}]+)\"?";

  /**
   * Built-in sensitive keys — always masked, non-configurable. These cover the most common
   * compliance requirements (GDPR, PCI-DSS).
   */
  private static final List<String> BUILT_IN_KEYS =
      List.of("password", "token", "cvv", "cardNumber", "secret", "authorization");

  private final List<String> allSensitiveKeys;

  /**
   * Constructs the masker by merging built-in defaults with consumer-configured fields.
   *
   * @param properties the library configuration properties
   */
  public JsonMasker(AuditLoggingProperties properties) {
    List<String> additional = properties.getMasking().getAdditionalFields();
    this.allSensitiveKeys =
        Stream.concat(BUILT_IN_KEYS.stream(), additional.stream()).distinct().toList();

    if (!additional.isEmpty()) {
      log.debug(
          "[AuditLog] JsonMasker initialised with {} built-in + {} additional sensitive fields.",
          BUILT_IN_KEYS.size(),
          additional.size());
    }
  }

  /**
   * Sanitizes a JSON string by masking values of all configured sensitive keys.
   *
   * @param json the raw JSON string to be processed
   * @return the sanitized JSON string, or {@code null} if the input was null
   */
  public String mask(String json) {
    if (json == null) return null;

    String masked = json;
    for (String key : allSensitiveKeys) {
      masked = masked.replaceAll(String.format(REGEX_PATTERN, key), "\"$1\":" + MASK);
    }
    return masked;
  }
}
