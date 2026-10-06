package com.api.audit.examples.annotation;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example: capturing inbound traffic with the {@code @AuditLog} annotation.
 *
 * <p>This is the original opt-in model. The annotation can be placed on a method (only that endpoint
 * is audited) or on the class (every endpoint in the class is audited unless a method-level
 * annotation overrides it).
 *
 * <p><b>What to look for in the audit record</b>
 *
 * <ul>
 *   <li>{@code description} equals the annotation value.
 *   <li>{@code type} is {@code INCOMING}, or {@code INCOMING_ERROR} when the request fails.
 * </ul>
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * GET  /examples/annotation/hello
 * POST /examples/annotation/echo   body: {"message":"hi"}
 * </pre>
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/annotation")
@AuditLog("Annotation: class-level default")
public class AnnotationCaptureExample {

  /**
   * Inherits the class-level annotation, so the description is the class-level value.
   *
   * @return a small payload
   */
  @GetMapping("/hello")
  public Map<String, String> hello() {
    return Map.of("message", "hello", "capturedBy", "@AuditLog class-level");
  }

  /**
   * Method-level annotation overrides the class-level description.
   *
   * @param body the request body
   * @return the echoed message
   */
  @PostMapping("/echo")
  @AuditLog("Annotation: echo with body")
  public Map<String, String> echo(@RequestBody Map<String, String> body) {
    return Map.of("echo", body.getOrDefault("message", ""));
  }
}
