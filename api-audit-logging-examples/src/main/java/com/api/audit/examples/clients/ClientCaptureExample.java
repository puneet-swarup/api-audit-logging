package com.api.audit.examples.clients;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Example: capturing outbound calls made by the supported HTTP clients.
 *
 * <p>Each endpoint triggers one outbound integration. Because the library propagates the {@code
 * X-Correlation-ID} header, the inbound record and the outbound record share the same correlation
 * ID, which is what makes cross-service tracing possible.
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * GET /examples/clients/feign/42
 * GET /examples/clients/resttemplate/42
 * GET /examples/clients/restclient/42
 * </pre>
 *
 * <p>The downstream URL is a placeholder by default; set {@code examples.downstream.base-url} to a
 * real service to see successful outbound capture, or leave it and observe {@code
 * OUTGOING_TRANSPORT_ERROR} records.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/clients")
public class ClientCaptureExample {

  private final InventoryFeignClient feignClient;
  private final RestTemplate restTemplate;
  private final RestClient restClient;

  /**
   * Creates the example with the audited clients injected.
   *
   * @param feignClient the audited Feign client
   * @param restTemplate a Spring-managed, audited RestTemplate
   * @param restClientBuilder a Spring-managed builder whose clients are audited
   */
  public ClientCaptureExample(
      InventoryFeignClient feignClient,
      RestTemplate restTemplate,
      RestClient.Builder restClientBuilder) {
    this.feignClient = feignClient;
    this.restTemplate = restTemplate;
    this.restClient =
        restClientBuilder
            .baseUrl("${examples.downstream.base-url:https://localhost.invalid}")
            .build();
  }

  /**
   * Triggers an audited outbound Feign call.
   *
   * @param sku the item to look up
   * @return a summary
   */
  @GetMapping("/feign/{sku}")
  @AuditLog("Client example: Feign")
  public Map<String, String> feign(@PathVariable String sku) {
    try {
      return Map.of("client", "feign", "response", feignClient.findItem(sku));
    } catch (RuntimeException ex) {
      return Map.of("client", "feign", "error", ex.getClass().getSimpleName());
    }
  }

  /**
   * Triggers an audited outbound RestTemplate call.
   *
   * @param sku the item to look up
   * @return a summary
   */
  @GetMapping("/resttemplate/{sku}")
  @AuditLog("Client example: RestTemplate")
  public Map<String, String> restTemplate(@PathVariable String sku) {
    try {
      ResponseEntity<String> response =
          restTemplate.getForEntity(
              "https://localhost.invalid/inventory/{sku}", String.class, sku);
      return Map.of("client", "resttemplate", "status", String.valueOf(response.getStatusCode()));
    } catch (RuntimeException ex) {
      return Map.of("client", "resttemplate", "error", ex.getClass().getSimpleName());
    }
  }

  /**
   * Triggers an audited outbound RestClient call.
   *
   * @param sku the item to look up
   * @return a summary
   */
  @GetMapping("/restclient/{sku}")
  @AuditLog("Client example: RestClient")
  public Map<String, String> restClient(@PathVariable String sku) {
    try {
      String body = restClient.get().uri("/inventory/{sku}", sku).retrieve().body(String.class);
      return Map.of("client", "restclient", "response", String.valueOf(body));
    } catch (RuntimeException ex) {
      return Map.of("client", "restclient", "error", ex.getClass().getSimpleName());
    }
  }
}
