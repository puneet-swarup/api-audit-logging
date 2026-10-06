package com.api.audit.mask;

/**
 * Service Provider Interface for redacting sensitive data from a captured payload before it reaches
 * any storage backend.
 *
 * <p>The library ships a JSON-aware implementation, {@link JsonTreePayloadMasker}, which walks the
 * parsed JSON tree and replaces the values of sensitive keys. Applications can supply their own
 * implementation (for example, one backed by a rules engine or a data-classification service) by
 * registering a bean of this type.
 *
 * <p><b>Contract:</b> Implementations must never throw. If a payload cannot be parsed or masked,
 * the implementation must return a safe fallback rather than propagate an exception, because
 * masking runs on the asynchronous audit path where a failure must not affect request handling.
 * Returning {@code null} is only appropriate when the input is {@code null}.
 *
 * <p><b>Thread safety:</b> The masker is invoked from the audit executor thread pool and must be
 * thread-safe.
 *
 * @author Puneet Swarup
 * @see JsonTreePayloadMasker
 */
public interface PayloadMasker {

  /**
   * Redacts sensitive data from the supplied payload.
   *
   * @param payload the raw payload string; may be {@code null}
   * @return the masked payload, or {@code null} when the input is {@code null}
   */
  String mask(String payload);
}
