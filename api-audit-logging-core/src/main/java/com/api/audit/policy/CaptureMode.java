package com.api.audit.policy;

/**
 * Controls how much of a request/response pair is copied into an audit record.
 *
 * <p>A policy (annotation-based, path-based, or custom) returns one of these modes as part of its
 * {@link AuditDecision}. The inbound capture filter then honours the mode when it assembles the
 * {@link com.api.audit.model.AuditLogRecord}.
 *
 * <p>Capture modes allow operators to audit high-volume endpoints without paying the cost of
 * copying large request or response bodies, while still recording method, URL, status, timing,
 * headers, and identity metadata.
 *
 * @author Puneet Swarup
 * @see AuditDecision
 */
public enum CaptureMode {

  /**
   * Capture the full record: request/response headers, request body, response body, status, timing,
   * and identity metadata.
   *
   * <p>This is the default mode and preserves the behavior of the library before capture modes were
   * introduced.
   */
  FULL,

  /**
   * Capture metadata only: method, URL, query string, status, timing, headers, client IP, user
   * agent, principal, and error details. Request and response bodies are omitted.
   *
   * <p>Use this for high-traffic endpoints where payload capture is unnecessary or privacy
   * sensitive, but the operational metadata (who called what, when, and with which outcome) is
   * still valuable.
   */
  METADATA_ONLY,

  /**
   * Capture bodies only, alongside the minimal routing metadata required to make the record
   * meaningful (method, URL, status, timing). Header capture is skipped.
   *
   * <p>Use this when payload inspection matters but headers may contain secrets managed outside the
   * library's masking rules.
   */
  BODY_ONLY
}
