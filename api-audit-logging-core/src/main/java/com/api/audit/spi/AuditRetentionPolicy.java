package com.api.audit.spi;

import java.time.LocalDateTime;

/**
 * Service Provider Interface for deleting expired audit records from a storage backend.
 *
 * <p>Retention is a storage concern: only the backend knows how to delete its own data efficiently.
 * A store that supports retention implements this interface; the library's retention scheduler then
 * invokes {@link #purgeBefore(LocalDateTime)} on the configured schedule. Stores that do not
 * implement it are simply never purged by the library.
 *
 * <p><b>Contract.</b> Implementations must be idempotent for a given cutoff, must never throw (a
 * failed purge must be logged, not propagated), and should return the number of records removed so
 * the caller can log and meter it.
 *
 * <p>A write-only sink such as Kafka does not implement this interface. A central pipeline owns its
 * own retention.
 *
 * @author Puneet Swarup
 * @see com.api.audit.config.AuditLoggingProperties.Cleanup
 */
public interface AuditRetentionPolicy {

  /**
   * Deletes audit records captured strictly before the given cutoff.
   *
   * @param cutoff records with a timestamp before this instant are eligible for deletion; never
   *     {@code null}
   * @return the number of records deleted; {@code 0} when nothing matched
   */
  long purgeBefore(LocalDateTime cutoff);
}
