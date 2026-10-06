package com.api.audit.examples.clients;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Example: WebClient response-body capture.
 *
 * <p>Metadata (method, URL, status, timing, headers, correlation ID) is always captured for WebClient
 * calls. Response-body capture is opt-in via {@code audit.logging.webclient.capture-bodies=true}. When
 * enabled, the response body is buffered, recorded on the audit record, and re-published so the
 * caller still receives it.
 *
 * <p><b>Try it</b> (with the {@code webclient-bodies} profile active)
 *
 * <pre>
 * GET /examples/clients/webclient-bodies/42
 * </pre>
 *
 * <p>The stored record's {@code response_body} contains the downstream payload.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/clients")
public class WebClientBodyCaptureExample {

  private final WebClient webClient;

  /**
   * Creates the example with an audited WebClient.
   *
   * @param builder a Spring-managed builder whose clients are audited
   */
  public WebClientBodyCaptureExample(WebClient.Builder builder) {
    this.webClient =
        builder.baseUrl("${examples.downstream.base-url:https://localhost.invalid}").build();
  }

  /**
   * Triggers an audited outbound WebClient call whose response body is captured when enabled.
   *
   * @param sku the item to look up
   * @return a summary
   */
  @GetMapping("/webclient-bodies/{sku}")
  @AuditLog("Client example: WebClient response body capture")
  public Map<String, String> webClientBodies(@PathVariable String sku) {
    try {
      String body =
          webClient.get().uri("/inventory/{sku}", sku).retrieve().bodyToMono(String.class).block();
      return Map.of("client", "webclient-bodies", "response", String.valueOf(body));
    } catch (RuntimeException ex) {
      return Map.of("client", "webclient-bodies", "error", ex.getClass().getSimpleName());
    }
  }
}
