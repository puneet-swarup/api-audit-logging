package com.api.audit.samples.demoApp.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller that is intentionally <b>not</b> annotated with {@code @AuditLog}.
 *
 * <p>It exists to prove the headline capability: when the {@code path-based} profile is active,
 * requests to these paths are captured purely from configuration, with no annotation and no
 * application code change. If the library and its configuration were removed, this controller would
 * keep compiling and behaving exactly the same — which is the definition of true pluggability.
 *
 * <p>Each endpoint below maps to a distinct rule in {@code application-path-based.yaml} so the
 * sample doubles as living documentation for every path-rule feature:
 *
 * <ul>
 *   <li>{@code /hello} — FULL capture with description and tags (broad Ant rule)
 *   <li>{@code /metadata} — METADATA_ONLY capture (no bodies)
 *   <li>{@code /body-only} — BODY_ONLY capture (no headers)
 *   <li>{@code /mutations} — method-scoped rule (POST/PUT/PATCH only)
 *   <li>{@code /orders/{id}} — regex matcher rule
 * </ul>
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/api/v1/path-audited")
public class PathAuditDemoController {

  /**
   * FULL capture: audited via the broad Ant rule with a description and tags.
   *
   * @return a small JSON payload
   */
  @GetMapping("/hello")
  public Map<String, String> hello() {
    return Map.of("message", "Hello from an unannotated controller", "status", "success");
  }

  /**
   * FULL capture for a POST body: audited via the broad Ant rule.
   *
   * @param body the request body; must contain a {@code message} field
   * @return a small JSON payload echoing the message
   */
  @PostMapping("/echo")
  public Map<String, String> echo(@RequestBody Map<String, String> body) {
    return Map.of("message", body.getOrDefault("message", "empty"), "status", "success");
  }

  /**
   * METADATA_ONLY capture: headers, status, and timing are recorded, but request and response
   * bodies are not.
   *
   * @return a small JSON payload
   */
  @GetMapping("/metadata/ping")
  public Map<String, String> metadataPing() {
    return Map.of("message", "metadata-only capture", "status", "success");
  }

  /**
   * BODY_ONLY capture: request and response bodies are recorded, but headers are not.
   *
   * @param body the request body
   * @return the echoed body
   */
  @PostMapping("/body-only/echo")
  public Map<String, String> bodyOnlyEcho(@RequestBody Map<String, String> body) {
    return Map.of("message", body.getOrDefault("message", "empty"), "status", "success");
  }

  /**
   * Method-scoped rule: audited only for POST, PUT, and PATCH; a GET on this path is not captured
   * by the mutation rule.
   *
   * @param body the request body
   * @return a small JSON payload
   */
  @PostMapping("/mutations")
  public Map<String, String> createMutation(@RequestBody(required = false) Map<String, String> body) {
    return Map.of("message", "mutation accepted", "status", "success");
  }

  /**
   * Method-scoped rule (GET is not audited by the mutation rule).
   *
   * @return a small JSON payload
   */
  @GetMapping("/mutations")
  public Map<String, String> readMutations() {
    return Map.of("message", "read mutations", "status", "success");
  }

  /**
   * Regex matcher rule: audited via {@code /api/v[0-9]+/path-audited/orders/.*}.
   *
   * @param id the order id
   * @return a small JSON payload
   */
  @GetMapping("/orders/{id}")
  public Map<String, String> order(@PathVariable String id) {
    return Map.of("orderId", id, "status", "success");
  }

  /**
   * PUT variant of the mutation endpoint to show method-scoped capture for updates.
   *
   * @param body the request body
   * @return a small JSON payload
   */
  @PutMapping("/mutations")
  public Map<String, String> updateMutation(@RequestBody(required = false) Map<String, String> body) {
    return Map.of("message", "mutation updated", "status", "success");
  }
}
