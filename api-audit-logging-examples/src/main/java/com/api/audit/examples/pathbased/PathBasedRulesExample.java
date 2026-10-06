package com.api.audit.examples.pathbased;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example: every path-based audit rule option, with no annotations anywhere.
 *
 * <p>This controller is intentionally unannotated. Capture is driven entirely by the rules in
 * {@code application-pathbased.yaml}. Each endpoint below maps to exactly one rule feature so you can
 * read one rule and one endpoint together.
 *
 * <p><b>Endpoints and the rule option they demonstrate</b>
 *
 * <ul>
 *   <li>{@code GET /examples/pathbased/hello} — Ant pattern + description + tags + FULL capture
 *   <li>{@code GET /examples/pathbased/metadata} — {@code capture: METADATA_ONLY} (no bodies)
 *   <li>{@code POST /examples/pathbased/body-only} — {@code capture: BODY_ONLY} (no headers)
 *   <li>{@code POST /examples/pathbased/mutations} — {@code methods: [POST, PUT, PATCH]}
 *   <li>{@code GET /examples/pathbased/orders/42} — regex matcher
 *   <li>{@code GET /examples/pathbased/skip} — explicit {@code audit: false}
 * </ul>
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/pathbased")
public class PathBasedRulesExample {

  /** Matched by a broad Ant rule that sets a description, tags, and FULL capture. */
  @GetMapping("/hello")
  public Map<String, String> hello() {
    return Map.of("message", "audited by a path rule", "feature", "ant + tags + FULL");
  }

  /** Matched by a rule with {@code capture: METADATA_ONLY}; bodies are not stored. */
  @GetMapping("/metadata")
  public Map<String, String> metadata() {
    return Map.of("message", "metadata-only capture", "feature", "METADATA_ONLY");
  }

  /** Matched by a rule with {@code capture: BODY_ONLY}; headers are not stored. */
  @PostMapping("/body-only")
  public Map<String, String> bodyOnly(@RequestBody Map<String, String> body) {
    return Map.of("echo", body.getOrDefault("message", ""), "feature", "BODY_ONLY");
  }

  /** Matched only for POST/PUT/PATCH by a method-scoped rule. */
  @PostMapping("/mutations")
  public Map<String, String> createMutation(
      @RequestBody(required = false) Map<String, String> body) {
    return Map.of("feature", "method-scoped rule (POST)");
  }

  /** A GET on the same path is not matched by the mutation rule. */
  @GetMapping("/mutations")
  public Map<String, String> readMutations() {
    return Map.of("feature", "method-scoped rule (GET not audited)");
  }

  /** Matched by a regex rule. */
  @GetMapping("/orders/{id}")
  public Map<String, String> order(@PathVariable String id) {
    return Map.of("orderId", id, "feature", "regex matcher");
  }

  /** Matched by an explicit skip rule; never audited. */
  @GetMapping("/skip")
  public Map<String, String> skip() {
    return Map.of("message", "this path is explicitly excluded", "feature", "audit: false");
  }

  /** Deletion endpoint, useful to show that only declared methods are audited. */
  @DeleteMapping("/mutations")
  public Map<String, String> deleteMutation() {
    return Map.of("feature", "method-scoped rule (DELETE not audited)");
  }
}
