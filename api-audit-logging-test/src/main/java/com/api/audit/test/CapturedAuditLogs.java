package com.api.audit.test;

import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.context.event.EventListener;

/**
 * Captures audit records published during a test so they can be asserted on.
 *
 * <p>This bean listens for {@link ApiLogEvent} on the Spring event bus and records each one. It is
 * registered by {@link EnableAuditLoggingTest} and injected into tests. It gives host applications
 * a simple, framework-native way to assert that their endpoints produce the audit records they
 * expect, without a database and without polling a store.
 *
 * <p><b>Example</b>
 *
 * <pre>{@code
 * @Autowired CapturedAuditLogs auditLogs;
 *
 * @Test
 * void capturesPayments() {
 *   mockMvc.perform(post("/api/v1/payments"));
 *   assertThat(auditLogs.single()).hasType("INCOMING");
 * }
 * }</pre>
 *
 * @author Puneet Swarup
 * @see AuditLogAssertions
 */
public class CapturedAuditLogs {

  private final List<AuditLogRecord> records = new ArrayList<>();

  /**
   * Records an audit event. Registered on the Spring event bus; called synchronously when a record
   * is published.
   *
   * @param event the published event; never {@code null}
   */
  @EventListener
  public void onAuditEvent(ApiLogEvent event) {
    records.add(event.record());
  }

  /**
   * Returns all captured records in publication order.
   *
   * @return an immutable snapshot of captured records; never {@code null}
   */
  public List<AuditLogRecord> all() {
    return List.copyOf(records);
  }

  /**
   * Returns the number of captured records.
   *
   * @return the captured record count
   */
  public int count() {
    return records.size();
  }

  /**
   * Returns whether any record matches the predicate.
   *
   * @param predicate the match condition
   * @return {@code true} when at least one record matches
   */
  public boolean anyMatch(Predicate<AuditLogRecord> predicate) {
    return records.stream().anyMatch(predicate);
  }

  /**
   * Returns whether no records have been captured.
   *
   * @return {@code true} when the capture list is empty
   */
  public boolean isEmpty() {
    return records.isEmpty();
  }

  /**
   * Returns the single captured record, failing if there is not exactly one.
   *
   * @return the sole captured record
   * @throws AssertionError when the count is not exactly one
   */
  public AuditLogAssertions single() {
    if (records.size() != 1) {
      throw new AssertionError("Expected exactly one audit record but captured " + records.size());
    }
    return new AuditLogAssertions(records.get(0));
  }

  /**
   * Returns an assertion object for the first captured record, failing if none were captured.
   *
   * @return assertions over the first record
   * @throws AssertionError when no records were captured
   */
  public AuditLogAssertions first() {
    if (records.isEmpty()) {
      throw new AssertionError("Expected at least one audit record but none were captured");
    }
    return new AuditLogAssertions(records.get(0));
  }

  /** Clears all captured records so the next assertion starts clean. */
  public void clear() {
    records.clear();
  }
}
