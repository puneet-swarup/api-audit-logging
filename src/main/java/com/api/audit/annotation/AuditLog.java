package com.api.audit.annotation;

import com.api.audit.interceptor.AuditLogInterceptor;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated method or type should be intercepted for automated API audit
 * logging.
 *
 * <p>When applied at the <b>Type (Class) level</b>, all public web-exposed methods within the class
 * are opted into the auditing lifecycle unless explicitly overridden. When applied at the <b>Method
 * level</b>, it enables auditing for that specific execution path.
 *
 * <p>The collected audit data typically includes request/response payloads, HTTP metadata,
 * execution duration, and correlation identifiers, which are subsequently processed by the {@code
 * AuditLogInterceptor} and published via the application event bus.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/payments")
 * @AuditLog("Payment Processing Controller")
 * public class PaymentController {
 *
 *     @PostMapping("/submit")
 *     @AuditLog("Submit Transaction")
 *     public ResponseEntity<String> submit() { ... }
 * }
 * }</pre>
 *
 * @author Puneet Swarup
 * @see AuditLogInterceptor
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

  /**
   * Defines a descriptive label or business operation name for the audit entry.
   *
   * <p>This value is persisted in the audit store to provide human-readable context for the logged
   * transaction. If left empty, the system may default to the URI or method name.
   *
   * @return the descriptive string for the operation, defaults to an empty string.
   */
  String value() default ""; // Optional description of the operation
}
