package com.api.audit.storage.memory;

import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the in-memory audit sink.
 *
 * <p>Applications normally choose this module for local demos or tests. It provides both write and
 * search capabilities, so the internal audit endpoint works without a database.
 *
 * @author Puneet Swarup
 */
@AutoConfiguration
@ConditionalOnExpression(
    "'${audit.logging.enabled:true}' == 'true' and '${audit.logging.storage.type:}' == 'memory'")
public class MemoryAuditLogAutoConfiguration {

  /** Registers a shared in-memory store when no other audit store has been provided. */
  @Bean
  @ConditionalOnMissingBean(AuditLogStore.class)
  public InMemoryAuditLogStore inMemoryAuditLogStore() {
    return new InMemoryAuditLogStore();
  }

  /** Exposes the in-memory store as the searchable backend for the internal endpoint. */
  @Bean
  @ConditionalOnMissingBean(AuditLogSearchStore.class)
  public AuditLogSearchStore inMemoryAuditLogSearchStore(InMemoryAuditLogStore store) {
    return store;
  }
}
