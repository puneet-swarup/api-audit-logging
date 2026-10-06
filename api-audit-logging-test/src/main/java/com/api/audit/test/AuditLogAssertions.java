package com.api.audit.test;

import com.api.audit.model.AuditLogRecord;

/**
 * Fluent assertions over a single {@link AuditLogRecord}.
 *
 * <p>Intentionally dependency-free (no AssertJ/TestNG coupling) so it works in any test framework.
 * Each method throws {@link AssertionError} on failure and returns {@code this} for chaining.
 *
 * <p><b>Example</b>
 *
 * <pre>{@code
 * auditLogs.single()
 *     .hasType("INCOMING")
 *     .hasMethod("POST")
 *     .hasStatus(202)
 *     .hasTag("module", "payments")
 *     .requestBodyContains("amount");
 * }</pre>
 *
 * @author Puneet Swarup
 */
public class AuditLogAssertions {

  private final AuditLogRecord record;

  /**
   * Creates the assertions.
   *
   * @param record the record to assert on; never {@code null}
   */
  public AuditLogAssertions(AuditLogRecord record) {
    this.record = record;
  }

  /**
   * Asserts the record type.
   *
   * @param expected the expected type (e.g. {@code INCOMING})
   * @return this
   */
  public AuditLogAssertions hasType(String expected) {
    if (!expected.equals(record.getType())) {
      throw new AssertionError(
          "Expected audit type '" + expected + "' but was '" + record.getType() + "'");
    }
    return this;
  }

  /**
   * Asserts the HTTP method.
   *
   * @param expected the expected method
   * @return this
   */
  public AuditLogAssertions hasMethod(String expected) {
    if (!expected.equals(record.getMethod())) {
      throw new AssertionError(
          "Expected method '" + expected + "' but was '" + record.getMethod() + "'");
    }
    return this;
  }

  /**
   * Asserts the response status.
   *
   * @param expected the expected HTTP status
   * @return this
   */
  public AuditLogAssertions hasStatus(int expected) {
    if (record.getHttpStatus() == null || record.getHttpStatus() != expected) {
      throw new AssertionError(
          "Expected status " + expected + " but was " + record.getHttpStatus());
    }
    return this;
  }

  /**
   * Asserts the URL contains a fragment.
   *
   * @param fragment the expected URL fragment
   * @return this
   */
  public AuditLogAssertions urlContains(String fragment) {
    String url = record.getUrl();
    if (url == null || !url.contains(fragment)) {
      throw new AssertionError("Expected URL to contain '" + fragment + "' but was '" + url + "'");
    }
    return this;
  }

  /**
   * Asserts the correlation ID.
   *
   * @param expected the expected correlation ID
   * @return this
   */
  public AuditLogAssertions hasCorrelationId(String expected) {
    if (!expected.equals(record.getCorrelationId())) {
      throw new AssertionError(
          "Expected correlationId '" + expected + "' but was '" + record.getCorrelationId() + "'");
    }
    return this;
  }

  /**
   * Asserts a tag key/value pair is present.
   *
   * @param key the tag key
   * @param value the expected tag value
   * @return this
   */
  public AuditLogAssertions hasTag(String key, String value) {
    String actual = record.getTags() == null ? null : record.getTags().get(key);
    if (!value.equals(actual)) {
      throw new AssertionError(
          "Expected tag '" + key + "' to be '" + value + "' but was '" + actual + "'");
    }
    return this;
  }

  /**
   * Asserts the request body contains a fragment.
   *
   * @param fragment the expected fragment
   * @return this
   */
  public AuditLogAssertions requestBodyContains(String fragment) {
    String body = record.getRequestBody();
    if (body == null || !body.contains(fragment)) {
      throw new AssertionError(
          "Expected request body to contain '" + fragment + "' but was '" + body + "'");
    }
    return this;
  }

  /**
   * Asserts the response body contains a fragment.
   *
   * @param fragment the expected fragment
   * @return this
   */
  public AuditLogAssertions responseBodyContains(String fragment) {
    String body = record.getResponseBody();
    if (body == null || !body.contains(fragment)) {
      throw new AssertionError(
          "Expected response body to contain '" + fragment + "' but was '" + body + "'");
    }
    return this;
  }

  /**
   * Asserts the request body does NOT contain a fragment (e.g. a masked secret).
   *
   * @param fragment the fragment that must be absent
   * @return this
   */
  public AuditLogAssertions requestBodyDoesNotContain(String fragment) {
    String body = record.getRequestBody();
    if (body != null && body.contains(fragment)) {
      throw new AssertionError(
          "Expected request body NOT to contain '" + fragment + "' but it did: " + body);
    }
    return this;
  }

  /**
   * @return the underlying record for further custom assertions
   */
  public AuditLogRecord record() {
    return record;
  }
}
