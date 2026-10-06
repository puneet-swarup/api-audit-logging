package com.api.audit.examples.masking;

import com.api.audit.mask.PayloadMasker;

/**
 * Example: a custom {@link PayloadMasker}.
 *
 * <p>The default {@code JsonTreePayloadMasker} redacts a configured set of field names. Some systems
 * need a different strategy — for example hashing identifiers instead of erasing them, or applying a
 * company-wide classification service. Implement {@link PayloadMasker} and expose it as a bean; it
 * automatically replaces the default.
 *
 * <p>This example delegates to the default masker first and then scrubs any value that looks like an
 * email address, regardless of field name. It shows the composition pattern: wrap the default rather
 * than reimplement it.
 *
 * <p><b>Contract:</b> implementations must never throw and must return {@code null} only for a
 * {@code null} input.
 *
 * @author Puneet Swarup
 */
public class CustomPayloadMasker implements PayloadMasker {

  private static final String EMAIL_REGEX = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";

  private final PayloadMasker delegate;

  /**
   * Creates the masker.
   *
   * @param delegate the masker to run first (typically the default JSON-tree masker); never {@code
   *     null}
   */
  public CustomPayloadMasker(PayloadMasker delegate) {
    this.delegate = delegate;
  }

  @Override
  public String mask(String payload) {
    String masked = delegate.mask(payload);
    if (masked == null) {
      return null;
    }
    return masked.replaceAll(EMAIL_REGEX, "******");
  }
}
