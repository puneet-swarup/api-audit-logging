package com.api.audit.examples.clients;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Example: capturing outbound WebClient metadata.
 *
 * <p>The WebClient integration captures method, URL, status, duration, and correlation ID. It does
 * not consume reactive request or response bodies, because doing so safely would require body
 * re-publishing.
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * GET /examples/clients/webclient/42
 * </pre>
 *
 * <p>The outbound call appears as an audit record with the same correlation ID as the inbound one.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/clients")
public class WebClientCaptureExample {

  private final WebClient webClient;

  /**
   * Creates the example with an audited WebClient.
   *
   * @param builder a Spring-managed builder whose clients are audited
   */
  public WebClientCaptureExample(WebClient.Builder builder) {
    this.webClient =
        builder.baseUrl("${examples.downstream.base-url:https://localhost.invalid}").build();
  }

  /**
   * Triggers an audited outbound WebClient call.
   *
   * @param sku the item to look up
   * @return a summary
   */
  @GetMapping("/webclient/{sku}")
  @AuditLog("Client example: WebClient")
  public Map<String, String> webClient(@PathVariable String sku) {
    try {
      String body =
          webClient.get().uri("/inventory/{sku}", sku).retrieve().bodyToMono(String.class).block();
      return Map.of("client", "webclient", "response", String.valueOf(body));
    } catch (RuntimeException ex) {
      return Map.of("client", "webclient", "error", ex.getClass().getSimpleName());
    }
  }
}
