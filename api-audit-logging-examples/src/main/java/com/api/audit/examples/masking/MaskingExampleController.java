package com.api.audit.examples.masking;

import com.api.audit.annotation.AuditLog;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example: payload masking of sensitive fields.
 *
 * <p>POST a body containing sensitive fields and inspect the stored record. Built-in fields
 * ({@code password}, {@code token}, {@code cvv}, {@code cardNumber}, {@code secret},
 * {@code authorization}) and any configured {@code masking.additional-fields} are redacted. The
 * default masker walks the JSON tree, so nested objects and arrays are covered too.
 *
 * <p><b>Try it</b>
 *
 * <pre>
 * POST /examples/masking/payment
 * {
 *   "amount": 100,
 *   "card": { "number": "4111111111111111", "cvv": "123" },
 *   "customer": { "email": "jane@example.com", "password": "hunter2" }
 * }
 * </pre>
 *
 * <p>In the stored record, {@code card.cvv} and {@code customer.password} are {@code ******}; with
 * the {@code masking} profile active the custom masker also scrubs {@code customer.email}.
 *
 * @author Puneet Swarup
 */
@RestController
@RequestMapping("/examples/masking")
public class MaskingExampleController {

  /**
   * Accepts a payment-like payload containing sensitive fields.
   *
   * @param body the request body
   * @return an acknowledgement
   */
  @PostMapping("/payment")
  @AuditLog("Masking: payment payload")
  public Map<String, Object> payment(@RequestBody Map<String, Object> body) {
    return Map.of("accepted", true, "receivedFields", body.keySet());
  }
}
