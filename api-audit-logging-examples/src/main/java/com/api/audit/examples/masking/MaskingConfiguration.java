package com.api.audit.examples.masking;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.mask.JsonTreePayloadMasker;
import com.api.audit.mask.PayloadMasker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a custom {@link PayloadMasker} for the {@code masking} profile.
 *
 * <p>Because a user-supplied {@code PayloadMasker} bean takes precedence over the library default,
 * this configuration is all that is required to change the masking strategy. It composes over the
 * default JSON-tree masker rather than replacing it outright.
 *
 * <p>Enable with {@code audit.logging.examples.masking.custom-masker=true} or the {@code masking}
 * profile.
 *
 * @author Puneet Swarup
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "audit.logging.examples.masking",
    name = "custom-masker",
    havingValue = "true")
public class MaskingConfiguration {

  /**
   * Registers the custom masker, wrapping the default JSON-tree masker.
   *
   * @param properties the library configuration properties
   * @return the custom payload masker
   */
  @Bean
  public PayloadMasker payloadMasker(AuditLoggingProperties properties) {
    return new CustomPayloadMasker(new JsonTreePayloadMasker(properties));
  }
}
