package com.api.audit.examples.policy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link HeaderBasedAuditPolicy} when the {@code policy} profile-like property is set.
 *
 * <p>Registering a custom policy is just exposing it as a Spring bean. The library's decision engine
 * discovers it automatically and orders it by {@code getOrder()}.
 *
 * <p>Enable with {@code audit.logging.examples.policy.enabled=true} or the {@code policy} profile.
 *
 * @author Puneet Swarup
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "audit.logging.examples.policy",
    name = "enabled",
    havingValue = "true")
public class CustomPolicyConfiguration {

  /**
   * Exposes the header-based policy as a bean.
   *
   * @return the custom policy
   */
  @Bean
  public HeaderBasedAuditPolicy headerBasedAuditPolicy() {
    return new HeaderBasedAuditPolicy();
  }
}
