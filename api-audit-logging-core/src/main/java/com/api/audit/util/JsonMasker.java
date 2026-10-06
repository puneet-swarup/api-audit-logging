package com.api.audit.util;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.mask.JsonTreePayloadMasker;
import com.api.audit.mask.PayloadMasker;
import org.springframework.stereotype.Component;

/**
 * Backward-compatible facade over the active {@link PayloadMasker}.
 *
 * <p>Historically this class performed masking itself using regular expressions. That approach
 * could corrupt payloads and only masked string-valued fields. Masking is now delegated to a {@link
 * PayloadMasker}, and the default implementation is the JSON-aware {@link JsonTreePayloadMasker}.
 *
 * <p>This facade is retained so existing callers and tests keep working unchanged. New code should
 * depend on {@link PayloadMasker} directly.
 *
 * <p>Example transformation:
 *
 * <pre>{@code
 * Input:  {"password": "mySecret123", "otp": "9876", "id": 101}
 * Output: {"password": "******", "otp": "******", "id": 101}
 * }</pre>
 *
 * @author Puneet Swarup
 * @see PayloadMasker
 * @see JsonTreePayloadMasker
 */
@Component
public class JsonMasker {

  private final PayloadMasker delegate;

  /**
   * Creates the facade using the default JSON-tree masker built from the supplied properties.
   *
   * @param properties the library configuration properties
   */
  public JsonMasker(AuditLoggingProperties properties) {
    this(new JsonTreePayloadMasker(properties));
  }

  /**
   * Creates the facade over an explicit delegate. Used by auto-configuration to inject the active
   * {@link PayloadMasker} bean (which may be a user-supplied implementation).
   *
   * @param delegate the masker to delegate to; never {@code null}
   */
  public JsonMasker(PayloadMasker delegate) {
    this.delegate = delegate;
  }

  /**
   * Sanitizes a payload by delegating to the configured {@link PayloadMasker}.
   *
   * @param json the raw payload string to be processed
   * @return the sanitized payload, or {@code null} if the input was null
   */
  public String mask(String json) {
    return delegate.mask(json);
  }
}
