package com.api.audit.samples.demoApp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Demo application used as executable documentation for the audit logging starter.
 *
 * @author Puneet Swarup
 */
@SpringBootApplication
public class ApiAuditDemoAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiAuditDemoAppApplication.class, args);
  }

  /**
   * RestTemplate built through Spring Boot so the audit starter can apply its outbound interceptor.
   */
  @Bean
  RestTemplate demoRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}
