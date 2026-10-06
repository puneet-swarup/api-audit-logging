package com.api.audit.examples.clients;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Provides the Spring-managed HTTP clients the client examples use.
 *
 * <p>The library customizes Spring-managed {@link RestTemplate} and {@link RestClient.Builder} beans,
 * so building them here (rather than with {@code new}) is what makes their outbound calls audited.
 * Feign clients are discovered through {@code @EnableFeignClients} on the application class.
 *
 * @author Puneet Swarup
 */
@Configuration(proxyBeanMethods = false)
public class ClientConfiguration {

  /**
   * Exposes an audited {@link RestTemplate}. The library's customizer adds the capturing interceptor
   * automatically.
   *
   * @param builder the Spring Boot RestTemplate builder
   * @return a RestTemplate that produces audit records for its calls
   */
  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }

  /**
   * Exposes an audited {@link RestClient.Builder}. Clients built from it capture outbound calls.
   *
   * @return a RestClient builder wired with the audit interceptor
   */
  @Bean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
