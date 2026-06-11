package com.api.audit.samples.demoApp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Demo application used as executable documentation for the audit logging starter.
 *
 * @author Puneet Swarup
 */
@SpringBootApplication
@EnableFeignClients
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

  /** WebClient builder managed by Spring Boot so the audit starter can add its exchange filter. */
  @Bean
  WebClient demoWebClient(WebClient.Builder builder) {
    return builder.build();
  }

  /** RestClient managed by Spring Boot so the audit starter can add its blocking interceptor. */
  @Bean
  RestClient demoRestClient(RestClient.Builder builder) {
    return builder.build();
  }

  /**
   * HTTP interface proxy backed by a Spring-managed RestClient builder.
   *
   * <p>This demonstrates how interface-based clients are covered without any audit-specific code in
   * the proxy itself.
   */
  @Bean
  DemoHttpInterfaceClient demoHttpInterfaceClient(RestClient demoRestClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(demoRestClient))
        .build()
        .createClient(DemoHttpInterfaceClient.class);
  }
}
