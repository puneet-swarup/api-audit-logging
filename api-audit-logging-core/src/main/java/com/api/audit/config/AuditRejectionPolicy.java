package com.api.audit.config;

/**
 * Controls what happens when the audit {@code logExecutor} queue is full.
 *
 * <p>Configure via {@code audit.logging.async.rejection-policy} in host application properties.
 *
 * <p>Choose based on compliance and latency requirements:
 *
 * <ul>
 *   <li>{@link #CALLER_RUNS} — the calling thread executes the audit task directly, applying
 *       backpressure. No audit record is ever lost. Recommended for legally required audit trails
 *       (GDPR, PCI-DSS, SOX, HIPAA). This is the default.
 *   <li>{@link #DISCARD_OLDEST} — the oldest pending audit record in the queue is dropped to make
 *       room for the new one. Zero latency impact on calling threads. Use for high-throughput,
 *       best-effort observability logging.
 *   <li>{@link #DISCARD} — the incoming (newest) audit record is dropped silently. Zero latency
 *       impact. Use when preserving in-flight records matters more than capturing new ones.
 *   <li>{@link #ABORT} — a {@link java.util.concurrent.RejectedExecutionException} is thrown. Use
 *       in strict fail-fast systems where silent loss is unacceptable.
 * </ul>
 *
 * @author Puneet Swarup
 */
public enum AuditRejectionPolicy {

  /**
   * Calling thread executes the audit task directly. No record is lost. Latency of the originating
   * request increases under sustained load.
   */
  CALLER_RUNS,

  /**
   * Oldest pending record in the queue is dropped. Zero caller latency. Recent events are preferred
   * over stale ones.
   */
  DISCARD_OLDEST,

  /** Incoming (newest) record is dropped. Zero caller latency. In-flight records are preserved. */
  DISCARD,

  /**
   * Throws {@link java.util.concurrent.RejectedExecutionException}. Failure is loud and visible —
   * no silent data loss.
   */
  ABORT
}
