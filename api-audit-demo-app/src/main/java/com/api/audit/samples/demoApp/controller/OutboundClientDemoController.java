package com.api.audit.samples.demoApp.controller;

import com.api.audit.annotation.AuditLog;
import com.api.audit.samples.demoApp.DemoFeignClient;
import com.api.audit.samples.demoApp.DemoHttpInterfaceClient;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Reference endpoints for the outbound HTTP client integrations.
 *
 * <p>Each endpoint calls the same downstream status API through a different Spring HTTP client. The
 * goal is to make the demo app useful as executable documentation: developers can run one service
 * and see how Feign, WebClient, RestClient, and HTTP interfaces are audited.
 *
 * @author Puneet Swarup
 */
@RestController
@RequiredArgsConstructor
public class OutboundClientDemoController {

  private final DemoFeignClient feignClient;
  private final WebClient demoWebClient;
  private final RestClient demoRestClient;
  private final DemoHttpInterfaceClient httpInterfaceClient;

  @Value("${demo.downstream.base-url:https://localhost.invalid}")
  private String downstreamBaseUrl;

  /** Demonstrates outbound audit capture for an annotated Feign client method. */
  @AuditLog("DEMO_FEIGN_FLOW")
  @GetMapping("/api/v1/demo/feign")
  public Map<String, Object> feign() {
    return feignClient.status(URI.create(downstreamBaseUrl), "feign");
  }

  /** Demonstrates outbound metadata capture for a Spring WebClient call. */
  @AuditLog("DEMO_WEBCLIENT_FLOW")
  @GetMapping("/api/v1/demo/webclient")
  public Map<String, Object> webClient() {
    return demoWebClient
        .get()
        .uri(downstreamBaseUrl + "/api/v1/status?client=webclient")
        .retrieve()
        .bodyToMono(Map.class)
        .block();
  }

  /** Demonstrates outbound audit capture for Spring's modern blocking RestClient. */
  @AuditLog("DEMO_RESTCLIENT_FLOW")
  @GetMapping("/api/v1/demo/restclient")
  public Map<String, Object> restClient() {
    return demoRestClient
        .get()
        .uri(downstreamBaseUrl + "/api/v1/status?client=restclient")
        .retrieve()
        .body(Map.class);
  }

  /** Demonstrates outbound audit capture for an HTTP interface backed by RestClient. */
  @AuditLog("DEMO_HTTP_INTERFACE_FLOW")
  @GetMapping("/api/v1/demo/http-interface")
  public Map<String, Object> httpInterface() {
    return httpInterfaceClient.status(
        URI.create(downstreamBaseUrl + "/api/v1/status"), "http-interface");
  }

  /** Endpoint used by the path-control demo profile to prove exclusions can suppress capture. */
  @AuditLog("DEMO_EXCLUDED_FLOW")
  @GetMapping("/api/v1/demo/no-audit/ping")
  public Map<String, Object> excludedByPathControl() {
    return Map.of("message", "this endpoint is excluded by the path-controls profile");
  }
}
