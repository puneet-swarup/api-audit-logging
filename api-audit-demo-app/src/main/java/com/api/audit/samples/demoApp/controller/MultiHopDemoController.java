package com.api.audit.samples.demoApp.controller;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Demo endpoint that performs an outbound call while handling an inbound audited request.
 *
 * <p>This gives the sample application a simple multi-hop flow: a caller hits this service, this
 * service calls another HTTP API, and both audit records should carry the same correlation ID.
 *
 * @author Puneet Swarup
 */
@RestController
@RequiredArgsConstructor
public class MultiHopDemoController {

  private final RestTemplate demoRestTemplate;

  @Value("${demo.downstream.status-url:https://localhost.invalid/api/v1/status?source=demo}")
  private String downstreamStatusUrl;

  /** Calls a downstream API so correlation propagation can be observed end to end. */
  @AuditLog("MULTI_HOP_AUDIT")
  @GetMapping("/api/v1/multi-hop")
  public Map<String, Object> multiHop() {
    @SuppressWarnings("unchecked")
    Map<String, Object> downstream =
        demoRestTemplate.getForObject(downstreamStatusUrl, Map.class);

    return Map.of(
        "message", "multi-hop complete",
        "downstream", downstream == null ? Map.of() : downstream);
  }
}
